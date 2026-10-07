package com.delivery.catalogo.service;

import com.delivery.catalogo.dto.ComercioRequest;
import com.delivery.catalogo.dto.ComercioResponse;
import com.delivery.catalogo.dto.ProductoRequest;
import com.delivery.catalogo.dto.ProductoResponse;
import com.delivery.catalogo.model.Comercio;
import com.delivery.catalogo.model.Producto;
import com.delivery.catalogo.model.StockReserva;
import com.delivery.catalogo.repository.ComercioRepository;
import com.delivery.catalogo.repository.ProductoRepository;
import com.delivery.catalogo.repository.StockReservaRepository;
import com.delivery.common.dto.StockReservaRequest;
import com.delivery.common.dto.StockReservaResponse;
import com.delivery.common.enums.CategoriaComercio;
import com.delivery.common.exception.InvalidStatusException;
import com.delivery.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CatalogoService {

    private static final Logger logger = LoggerFactory.getLogger(CatalogoService.class);

    private final ComercioRepository comercioRepository;
    private final ProductoRepository productoRepository;
    private final StockReservaRepository stockReservaRepository;

    @Transactional
    public ComercioResponse crearComercio(ComercioRequest request) {
        Comercio comercio = comercioRepository.save(Comercio.builder()
                .nombre(request.getNombre())
                .categoria(request.getCategoria())
                .direccion(request.getDireccion())
                .abierto(!Boolean.FALSE.equals(request.getAbierto()))
                .build());
        return toComercioResponse(comercio);
    }

    @Transactional
    public ProductoResponse crearProducto(Long comercioId, ProductoRequest request) {
        Comercio comercio = comercioRepository.findById(comercioId)
                .orElseThrow(() -> new ResourceNotFoundException("Comercio no encontrado"));

        Producto producto = productoRepository.save(Producto.builder()
                .nombre(request.getNombre())
                .precio(request.getPrecio())
                .stock(request.getStock())
                .disponible(!Boolean.FALSE.equals(request.getDisponible()))
                .comercio(comercio)
                .build());
        return toProductoResponse(producto);
    }

    @Transactional(readOnly = true)
    public List<ComercioResponse> listarComercios(CategoriaFiltro categoria, Pageable pageable) {
        List<Comercio> comercios = (categoria == null || categoria.valor() == null)
                ? comercioRepository.findComerciosAbiertos(pageable)
                : comercioRepository.findByCategoriaAndAbierto(categoria.valor(), pageable);
        return comercios.stream().map(this::toComercioResponse).toList();
    }

    @Transactional(readOnly = true)
    public ComercioResponse obtenerComercio(Long id) {
        Comercio comercio = comercioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comercio no encontrado"));
        return toComercioResponse(comercio);
    }

    @Transactional(readOnly = true)
    public List<ProductoResponse> listarProductos(Long comercioId, Boolean soloDisponibles, Pageable pageable) {
        if (!comercioRepository.existsById(comercioId)) {
            throw new ResourceNotFoundException("Comercio no encontrado");
        }
        List<Producto> productos = Boolean.TRUE.equals(soloDisponibles)
                ? productoRepository.findByComercioIdDisponibles(comercioId, pageable)
                : productoRepository.findByComercioId(comercioId, pageable);
        return productos.stream()
                .map(this::toProductoResponse)
                .toList();
    }

    @Transactional
    public StockReservaResponse reservarStock(StockReservaRequest request) {
        logger.info("Iniciando reserva de stock con reservaId: {}", request.getReservaId());

        if (stockReservaRepository.existsByReservaId(request.getReservaId())) {
            logger.warn("ReservaId {} ya existe", request.getReservaId());
            return StockReservaResponse.builder()
                    .exito(false)
                    .mensaje("ReservaId ya existe")
                    .build();
        }

        List<Long> productoIds = request.getItems().stream()
                .map(StockReservaRequest.StockItemRequest::getProductoId)
                .distinct()
                .sorted()
                .toList();

        List<Producto> productos = productoRepository.findByIdsWithLock(productoIds);

        if (productos.size() != productoIds.size()) {
            logger.warn("Uno o mas productos no existen");
            return StockReservaResponse.builder()
                    .exito(false)
                    .mensaje("Uno o mas productos no existen")
                    .build();
        }

        Map<Long, Producto> productoMap = productos.stream()
                .collect(Collectors.toMap(Producto::getId, p -> p));

        Map<Long, Integer> cantidadesSolicitadas = request.getItems().stream()
                .collect(Collectors.groupingBy(
                        StockReservaRequest.StockItemRequest::getProductoId,
                        Collectors.summingInt(StockReservaRequest.StockItemRequest::getCantidad)
                ));

        for (Map.Entry<Long, Integer> entry : cantidadesSolicitadas.entrySet()) {
            Producto producto = productoMap.get(entry.getKey());
            if (producto.getStock() < entry.getValue()) {
                logger.warn("Stock insuficiente para producto {}: disponible={}, solicitado={}",
                        producto.getNombre(), producto.getStock(), entry.getValue());
                return StockReservaResponse.builder()
                        .exito(false)
                        .mensaje(String.format("Stock insuficiente para %s. Disponible: %d, Solicitado: %d",
                                producto.getNombre(), producto.getStock(), entry.getValue()))
                        .build();
            }
        }

        List<StockReserva> reservas = new ArrayList<>();
        List<StockReservaResponse.ProductoReservado> productosReservados = new ArrayList<>();

        for (Map.Entry<Long, Integer> entry : cantidadesSolicitadas.entrySet()) {
            Producto producto = productoMap.get(entry.getKey());
            int cantidad = entry.getValue();

            producto.setStock(producto.getStock() - cantidad);

            StockReserva reserva = StockReserva.builder()
                    .reservaId(request.getReservaId())
                    .producto(producto)
                    .cantidad(cantidad)
                    .estado(StockReserva.EstadoReserva.RESERVADA)
                    .fechaExpiracion(LocalDateTime.now().plusMinutes(5))
                    .build();

            reservas.add(reserva);
            productosReservados.add(StockReservaResponse.ProductoReservado.builder()
                    .productoId(producto.getId())
                    .nombre(producto.getNombre())
                    .precio(producto.getPrecio())
                    .cantidad(cantidad)
                    .build());
        }

        stockReservaRepository.saveAll(reservas);

        logger.info("Stock reservado exitosamente para reservaId: {}", request.getReservaId());

        return StockReservaResponse.builder()
                .exito(true)
                .mensaje("Stock reservado exitosamente")
                .productos(productosReservados)
                .build();
    }

    @Transactional
    public void liberarStock(String reservaId) {
        logger.info("Liberando stock para reservaId: {}", reservaId);

        List<StockReserva> reservas = stockReservaRepository.findByReservaId(reservaId);
        if (reservas.isEmpty()) {
            logger.warn("No se encontro reserva con reservaId: {}", reservaId);
            return;
        }

        for (StockReserva reserva : reservas) {
            if (reserva.getEstado() == StockReserva.EstadoReserva.LIBERADA) {
                logger.info("Reserva {} ya esta liberada, no-op", reservaId);
                continue;
            }
            Producto producto = productoRepository.findByIdWithLock(reserva.getProducto().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));
            producto.setStock(producto.getStock() + reserva.getCantidad());
            reserva.setEstado(StockReserva.EstadoReserva.LIBERADA);
            stockReservaRepository.save(reserva);
            logger.info("Stock restaurado para producto {}: cantidad {}",
                    producto.getNombre(), reserva.getCantidad());
        }

        logger.info("Stock liberado exitosamente para reservaId: {}", reservaId);
    }

    @Transactional
    public void confirmarReserva(String reservaId) {
        logger.info("Confirmando reserva: {}", reservaId);

        List<StockReserva> reservas = stockReservaRepository.findByReservaId(reservaId);
        for (StockReserva reserva : reservas) {
            if (reserva.getEstado() == StockReserva.EstadoReserva.RESERVADA) {
                reserva.setEstado(StockReserva.EstadoReserva.CONFIRMADA);
                stockReservaRepository.save(reserva);
            }
        }

        logger.info("Reserva confirmada: {}", reservaId);
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void liberarReservasExpiradas() {
        LocalDateTime now = LocalDateTime.now();
        List<StockReserva> expiradas = stockReservaRepository.findReservasExpiradas(now);
        if (!expiradas.isEmpty()) {
            logger.info("Liberando {} reservas expiradas", expiradas.size());
            for (StockReserva reserva : expiradas) {
                try {
                    Producto producto = productoRepository.findByIdWithLock(reserva.getProducto().getId())
                            .orElse(null);
                    if (producto != null) {
                        producto.setStock(producto.getStock() + reserva.getCantidad());
                    }
                    reserva.setEstado(StockReserva.EstadoReserva.LIBERADA);
                    stockReservaRepository.save(reserva);
                } catch (Exception e) {
                    logger.error("Error al liberar reserva expirada con id {}", reserva.getId(), e);
                }
            }
        }
    }

    public record CategoriaFiltro(CategoriaComercio valor) {
        public static CategoriaFiltro of(String raw) {
            if (raw == null || raw.isBlank()) {
                return new CategoriaFiltro(null);
            }
            try {
                return new CategoriaFiltro(CategoriaComercio.valueOf(raw.trim().toUpperCase()));
            } catch (IllegalArgumentException ex) {
                throw new InvalidStatusException(
                        "Categoria invalida: " + raw + ". Valores: RESTAURANTE, SUPERMERCADO, FARMACIA");
            }
        }
    }

    private ComercioResponse toComercioResponse(Comercio comercio) {
        return ComercioResponse.builder()
                .id(comercio.getId())
                .nombre(comercio.getNombre())
                .categoria(comercio.getCategoria())
                .direccion(comercio.getDireccion())
                .abierto(comercio.getAbierto())
                .build();
    }

    private ProductoResponse toProductoResponse(Producto producto) {
        return ProductoResponse.builder()
                .id(producto.getId())
                .nombre(producto.getNombre())
                .precio(producto.getPrecio())
                .stock(producto.getStock())
                .disponible(producto.getDisponible())
                .comercioId(producto.getComercio().getId())
                .build();
    }
}
