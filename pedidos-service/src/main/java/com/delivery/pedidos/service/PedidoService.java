package com.delivery.pedidos.service;

import com.delivery.common.dto.StockReservaRequest;
import com.delivery.common.dto.StockReservaResponse;
import com.delivery.common.enums.EstadoPedido;
import com.delivery.common.enums.Rol;
import com.delivery.common.exception.ConflictException;
import com.delivery.common.exception.InsufficientStockException;
import com.delivery.common.exception.InvalidStatusException;
import com.delivery.common.exception.ResourceNotFoundException;
import com.delivery.pedidos.client.CatalogoClient;
import com.delivery.pedidos.dto.CambioEstadoRequest;
import com.delivery.pedidos.dto.PedidoRequest;
import com.delivery.pedidos.dto.PedidoResponse;
import com.delivery.pedidos.model.DetallePedido;
import com.delivery.pedidos.model.Pedido;
import com.delivery.pedidos.repository.PedidoRepository;
import com.delivery.pedidos.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private static final BigDecimal COSTO_ENVIO = new BigDecimal("20.00");
    private static final Set<EstadoPedido> ESTADOS_AUTO_ASIGNABLES = Set.of(
            EstadoPedido.PENDIENTE, EstadoPedido.EN_CAMINO);

    private static final Map<EstadoPedido, Set<EstadoPedido>> TRANSICIONES = new EnumMap<>(EstadoPedido.class);

    static {
        TRANSICIONES.put(EstadoPedido.PENDIENTE, Set.of(EstadoPedido.EN_PREPARACION));
        TRANSICIONES.put(EstadoPedido.EN_PREPARACION, Set.of(EstadoPedido.EN_CAMINO));
        TRANSICIONES.put(EstadoPedido.EN_CAMINO, Set.of(EstadoPedido.ENTREGADO));
        TRANSICIONES.put(EstadoPedido.ENTREGADO, Set.of());
        TRANSICIONES.put(EstadoPedido.CANCELADO, Set.of());
    }

    private final PedidoRepository pedidoRepository;
    private final CatalogoClient catalogoClient;

    @Transactional
    public PedidoResponse crearPedido(Long clienteId, String clienteNombre, PedidoRequest request) {
        Map<Long, Integer> cantidades = consolidar(request);
        List<StockReservaRequest.StockItemRequest> items = cantidades.entrySet().stream()
                .map(e -> StockReservaRequest.StockItemRequest.builder()
                        .productoId(e.getKey())
                        .cantidad(e.getValue())
                        .build())
                .toList();

        String reservaId = UUID.randomUUID().toString();
        StockReservaResponse reserva = catalogoClient.reservar(reservaId, items);
        if (!reserva.isExito()) {
            throw new InsufficientStockException(reserva.getMensaje());
        }

        Map<Long, StockReservaResponse.ProductoReservado> porId = new LinkedHashMap<>();
        for (StockReservaResponse.ProductoReservado p : reserva.getProductos()) {
            porId.put(p.getProductoId(), p);
        }

        Pedido pedido = Pedido.builder()
                .clienteId(clienteId)
                .clienteNombre(clienteNombre)
                .reservaId(reservaId)
                .estado(EstadoPedido.PENDIENTE)
                .costoEnvio(COSTO_ENVIO)
                .fechaPedido(java.time.LocalDateTime.now())
                .build();

        BigDecimal subtotalTotal = BigDecimal.ZERO;
        for (Long productoId : porId.keySet()) {
            StockReservaResponse.ProductoReservado reservado = porId.get(productoId);
            int cantidad = reservado.getCantidad();
            BigDecimal subtotal = reservado.getPrecio().multiply(BigDecimal.valueOf(cantidad))
                    .setScale(2, RoundingMode.HALF_UP);
            subtotalTotal = subtotalTotal.add(subtotal);

            DetallePedido detalle = DetallePedido.builder()
                    .productoId(productoId)
                    .productoNombre(reservado.getNombre())
                    .cantidad(cantidad)
                    .precioUnitario(reservado.getPrecio())
                    .subtotal(subtotal)
                    .build();
            pedido.addDetalle(detalle);
        }

        pedido.setMontoTotal(subtotalTotal.add(COSTO_ENVIO).setScale(2, RoundingMode.HALF_UP));

        try {
            pedido = pedidoRepository.saveAndFlush(pedido);
        } catch (RuntimeException ex) {
            catalogoClient.liberar(reservaId);
            throw ex;
        }

        catalogoClient.confirmar(reservaId);
        return toResponse(pedido);
    }

    @Transactional
    public PedidoResponse cancelarPedido(Long pedidoId) {
        Long actorId = SecurityUtils.currentUserId()
                .orElseThrow(() -> new InvalidStatusException("Autenticacion requerida"));

        Pedido pedido = pedidoRepository.findByIdConDetallesParaActualizar(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));

        Rol rol = SecurityUtils.currentRol().orElse(Rol.CLIENTE);
        if (rol == Rol.CLIENTE && !pedido.getClienteId().equals(actorId)) {
            throw new ConflictException("No puede cancelar un pedido de otro cliente");
        }
        if (pedido.getEstado() != EstadoPedido.PENDIENTE) {
            throw new InvalidStatusException("Solo se pueden cancelar pedidos en estado PENDIENTE");
        }

        pedido.setEstado(EstadoPedido.CANCELADO);
        pedido = pedidoRepository.saveAndFlush(pedido);

        catalogoClient.liberar(pedido.getReservaId());
        return toResponse(pedido);
    }

    @Transactional
    public PedidoResponse cambiarEstado(Long pedidoId, CambioEstadoRequest request) {
        EstadoPedido nuevoEstado = request.getEstado();
        if (nuevoEstado == EstadoPedido.CANCELADO) {
            throw new InvalidStatusException("Use el endpoint de cancelacion para cancelar un pedido");
        }

        Pedido pedido = pedidoRepository.findByIdConDetallesParaActualizar(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));

        Set<EstadoPedido> permitidos = TRANSICIONES.get(pedido.getEstado());
        if (permitidos == null || !permitidos.contains(nuevoEstado)) {
            throw new InvalidStatusException("Transicion invalida de " + pedido.getEstado() + " a " + nuevoEstado);
        }

        Optional<Rol> rol = SecurityUtils.currentRol();
        if (rol.isPresent() && rol.get() == Rol.REPARTIDOR && pedido.getRepartidorId() == null) {
            pedido.setRepartidorId(SecurityUtils.currentUserId().orElse(null));
            pedido.setRepartidorNombre(SecurityUtils.currentUserName().orElse(null));
        }

        pedido.setEstado(nuevoEstado);
        pedido = pedidoRepository.saveAndFlush(pedido);
        return toResponse(pedido);
    }

    @Transactional(readOnly = true)
    public List<PedidoResponse> misPedidos(Long clienteId) {
        List<Pedido> pedidos = pedidoRepository.findByClienteId(clienteId);
        return pedidos.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PedidoResponse> disponibles() {
        List<Pedido> pedidos = pedidoRepository.findByEstadoIn(ESTADOS_AUTO_ASIGNABLES);
        return pedidos.stream().map(this::toResponse).toList();
    }

    private Map<Long, Integer> consolidar(PedidoRequest request) {
        Map<Long, Integer> cantidades = new LinkedHashMap<>();
        for (PedidoRequest.ItemPedidoRequest item : request.getProductos()) {
            cantidades.merge(item.getProductoId(), item.getCantidad(), Integer::sum);
        }
        return cantidades;
    }

    private PedidoResponse toResponse(Pedido pedido) {
        List<DetallePedido> lineas = pedido.getDetalles() == null ? List.of() : pedido.getDetalles();
        List<PedidoResponse.DetallePedidoResponse> detalles = lineas
                .stream()
                .sorted(Comparator.comparing(DetallePedido::getProductoId))
                .map(d -> PedidoResponse.DetallePedidoResponse.builder()
                        .productoId(d.getProductoId())
                        .productoNombre(d.getProductoNombre())
                        .cantidad(d.getCantidad())
                        .precioUnitario(d.getPrecioUnitario())
                        .subtotal(d.getSubtotal())
                        .build())
                .toList();

        return PedidoResponse.builder()
                .id(pedido.getId())
                .clienteId(pedido.getClienteId())
                .clienteNombre(pedido.getClienteNombre())
                .repartidorId(pedido.getRepartidorId())
                .repartidorNombre(pedido.getRepartidorNombre())
                .estado(pedido.getEstado())
                .fechaPedido(pedido.getFechaPedido())
                .costoEnvio(pedido.getCostoEnvio())
                .montoTotal(pedido.getMontoTotal())
                .productos(detalles)
                .build();
    }
}
