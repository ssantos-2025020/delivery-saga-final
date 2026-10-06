package com.fastorder.service;

import com.fastorder.dto.*;
import com.fastorder.entity.*;
import com.fastorder.exception.InsufficientStockException;
import com.fastorder.exception.InvalidStatusException;
import com.fastorder.exception.ResourceNotFoundException;
import com.fastorder.repository.PedidoRepository;
import com.fastorder.repository.ProductoRepository;
import com.fastorder.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;

    private static final BigDecimal COSTO_ENVIO_FIJO = new BigDecimal("20.00");

    @Transactional
    public PedidoResponse crearPedido(CrearPedidoRequest request, String clienteEmail) {
        Usuario cliente = usuarioRepository.findByEmail(clienteEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario cliente no encontrado: " + clienteEmail));

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("El pedido debe contener al menos un item");
        }

        // 1. Agrupar y ordenar IDs de productos para evitar deadlocks (orden ASC)
        Map<Long, Integer> cantidadesPorProducto = new LinkedHashMap<>();
        for (CrearPedidoItemRequest item : request.getItems()) {
            if (item.getCantidad() == null || item.getCantidad() < 1) {
                throw new IllegalArgumentException("La cantidad debe ser mayor o igual a 1");
            }
            cantidadesPorProducto.merge(item.getProductoId(), item.getCantidad(), Integer::sum);
        }

        List<Long> productoIdsOrdenados = cantidadesPorProducto.keySet().stream()
                .sorted()
                .collect(Collectors.toList());

        // 2. Bloqueo pesimista PESSIMISTIC_WRITE sobre los productos en orden por ID
        List<Producto> productosBloqueados = productoRepository.findAllByIdInOrderByIdAscForUpdate(productoIdsOrdenados);

        Map<Long, Producto> mapaProductos = productosBloqueados.stream()
                .collect(Collectors.toMap(Producto::getId, p -> p));

        // 3. Validar existencia de todos los productos
        for (Long prodId : productoIdsOrdenados) {
            if (!mapaProductos.containsKey(prodId)) {
                throw new ResourceNotFoundException("Producto no encontrado con ID: " + prodId);
            }
        }

        // 4. Validar que todos los productos pertenezcan al mismo comercio y que este abierto
        Comercio comercioReferencia = null;
        for (Producto prod : productosBloqueados) {
            if (comercioReferencia == null) {
                comercioReferencia = prod.getComercio();
                if (Boolean.FALSE.equals(comercioReferencia.getAbierto())) {
                    throw new IllegalStateException("El comercio '" + comercioReferencia.getNombre() + "' se encuentra cerrado");
                }
            } else if (!comercioReferencia.getId().equals(prod.getComercio().getId())) {
                throw new IllegalArgumentException("Todos los productos de un pedido deben pertenecer al mismo comercio");
            }
        }

        // 5. Validar disponibilidad y stock suficiente
        for (Map.Entry<Long, Integer> entry : cantidadesPorProducto.entrySet()) {
            Producto prod = mapaProductos.get(entry.getKey());
            int cantidadSolicitada = entry.getValue();

            if (Boolean.FALSE.equals(prod.getDisponible())) {
                throw new IllegalStateException("El producto '" + prod.getNombre() + "' no esta disponible para pedidos");
            }

            if (prod.getStock() < cantidadSolicitada) {
                throw new InsufficientStockException(
                        String.format("Stock insuficiente para el producto '%s' (ID %d). Disponible: %d, Solicitado: %d",
                                prod.getNombre(), prod.getId(), prod.getStock(), cantidadSolicitada)
                );
            }
        }

        // 6. Descontar stock y calcular subtotales
        BigDecimal sumaSubtotales = BigDecimal.ZERO;
        List<DetallePedido> detalles = new ArrayList<>();

        Pedido pedido = Pedido.builder()
                .cliente(cliente)
                .repartidor(null)
                .fechaPedido(LocalDateTime.now())
                .costoEnvio(COSTO_ENVIO_FIJO)
                .montoTotal(BigDecimal.ZERO)
                .estado(EstadoPedido.PENDIENTE)
                .build();

        for (Map.Entry<Long, Integer> entry : cantidadesPorProducto.entrySet()) {
            Producto prod = mapaProductos.get(entry.getKey());
            int cantidad = entry.getValue();

            prod.setStock(prod.getStock() - cantidad);
            productoRepository.save(prod);

            BigDecimal precioUnitario = prod.getPrecio();
            BigDecimal subtotal = precioUnitario.multiply(BigDecimal.valueOf(cantidad));
            sumaSubtotales = sumaSubtotales.add(subtotal);

            DetallePedido detalle = DetallePedido.builder()
                    .pedido(pedido)
                    .producto(prod)
                    .cantidad(cantidad)
                    .precioUnitario(precioUnitario)
                    .subtotal(subtotal)
                    .build();

            detalles.add(detalle);
        }

        pedido.setDetalles(detalles);
        pedido.setMontoTotal(sumaSubtotales.add(COSTO_ENVIO_FIJO));

        Pedido pedidoGuardado = pedidoRepository.save(pedido);
        return mapToResponse(pedidoGuardado);
    }

    @Transactional(readOnly = true)
    public List<PedidoResponse> obtenerMisPedidos(String clienteEmail) {
        Usuario cliente = usuarioRepository.findByEmail(clienteEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario cliente no encontrado: " + clienteEmail));

        return pedidoRepository.findByClienteIdOrderByFechaPedidoDesc(cliente.getId()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PedidoResponse> obtenerPedidosDisponibles(String userEmail) {
        Usuario usuario = usuarioRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + userEmail));

        List<Pedido> pedidos;
        if (usuario.getRol() == Rol.ADMIN) {
            // ADMIN: todos los pedidos no finalizados
            pedidos = pedidoRepository.findByEstadoNotInOrderByFechaPedidoDesc(
                    List.of(EstadoPedido.ENTREGADO, EstadoPedido.CANCELADO)
            );
        } else if (usuario.getRol() == Rol.REPARTIDOR) {
            // REPARTIDOR: pedidos en EN_PREPARACION sin repartidor asignado
            pedidos = pedidoRepository.findByEstadoAndRepartidorIsNullOrderByFechaPedidoAsc(
                    EstadoPedido.EN_PREPARACION
            );
        } else {
            throw new AccessDeniedException("No tiene permisos para consultar pedidos disponibles");
        }

        return pedidos.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public PedidoResponse cambiarEstadoPedido(Long pedidoId, EstadoPedido nuevoEstado, String userEmail) {
        Usuario usuario = usuarioRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + userEmail));

        Pedido pedido = pedidoRepository.findByIdWithDetalles(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + pedidoId));

        EstadoPedido actual = pedido.getEstado();

        if (actual == EstadoPedido.ENTREGADO || actual == EstadoPedido.CANCELADO) {
            throw new InvalidStatusException("No se puede modificar un pedido finalizado en estado " + actual);
        }

        if (usuario.getRol() == Rol.ADMIN) {
            // ADMIN puede cualquier transicion secuencial valida
            boolean valida = false;
            if (actual == EstadoPedido.PENDIENTE && nuevoEstado == EstadoPedido.EN_PREPARACION) valida = true;
            else if (actual == EstadoPedido.EN_PREPARACION && nuevoEstado == EstadoPedido.EN_CAMINO) valida = true;
            else if (actual == EstadoPedido.EN_CAMINO && nuevoEstado == EstadoPedido.ENTREGADO) valida = true;

            if (!valida) {
                throw new InvalidStatusException(String.format("Transicion invalida de %s a %s", actual, nuevoEstado));
            }
            pedido.setEstado(nuevoEstado);

        } else if (usuario.getRol() == Rol.REPARTIDOR) {
            // REPARTIDOR solo:
            // EN_PREPARACION -> EN_CAMINO (se asigna a si mismo)
            // EN_CAMINO -> ENTREGADO (solo si es el repartidor asignado)
            if (actual == EstadoPedido.EN_PREPARACION && nuevoEstado == EstadoPedido.EN_CAMINO) {
                pedido.setRepartidor(usuario);
                pedido.setEstado(EstadoPedido.EN_CAMINO);
            } else if (actual == EstadoPedido.EN_CAMINO && nuevoEstado == EstadoPedido.ENTREGADO) {
                if (pedido.getRepartidor() == null || !pedido.getRepartidor().getId().equals(usuario.getId())) {
                    throw new AccessDeniedException("Solo el repartidor asignado al pedido puede marcarlo como ENTREGADO");
                }
                pedido.setEstado(EstadoPedido.ENTREGADO);
            } else {
                throw new InvalidStatusException(String.format("Transicion no permitida para repartidor: %s a %s", actual, nuevoEstado));
            }
        } else {
            throw new AccessDeniedException("No tiene permisos para cambiar el estado del pedido");
        }

        Pedido guardado = pedidoRepository.save(pedido);
        return mapToResponse(guardado);
    }

    @Transactional
    public PedidoResponse cancelarPedido(Long pedidoId, String userEmail) {
        Usuario usuario = usuarioRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + userEmail));

        Pedido pedido = pedidoRepository.findByIdWithDetalles(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + pedidoId));

        if (pedido.getEstado() != EstadoPedido.PENDIENTE) {
            throw new InvalidStatusException("Solo se pueden cancelar pedidos en estado PENDIENTE. Estado actual: " + pedido.getEstado());
        }

        if (usuario.getRol() == Rol.CLIENTE && !pedido.getCliente().getId().equals(usuario.getId())) {
            throw new AccessDeniedException("No tiene permisos para cancelar este pedido");
        }

        pedido.setEstado(EstadoPedido.CANCELADO);

        // Restaurar stock en orden ASC de producto para evitar deadlocks
        List<DetallePedido> detallesOrdenados = pedido.getDetalles().stream()
                .sorted(Comparator.comparing(d -> d.getProducto().getId()))
                .collect(Collectors.toList());

        for (DetallePedido detalle : detallesOrdenados) {
            Producto producto = productoRepository.findByIdForUpdate(detalle.getProducto().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + detalle.getProducto().getId()));
            producto.setStock(producto.getStock() + detalle.getCantidad());
            productoRepository.save(producto);
        }

        Pedido guardado = pedidoRepository.save(pedido);
        return mapToResponse(guardado);
    }

    public PedidoResponse mapToResponse(Pedido pedido) {
        List<DetallePedidoResponse> detalleResponses = pedido.getDetalles() == null
                ? Collections.emptyList()
                : pedido.getDetalles().stream()
                .map(d -> DetallePedidoResponse.builder()
                        .id(d.getId())
                        .productoId(d.getProducto().getId())
                        .productoNombre(d.getProducto().getNombre())
                        .cantidad(d.getCantidad())
                        .precioUnitario(d.getPrecioUnitario())
                        .subtotal(d.getSubtotal())
                        .build())
                .collect(Collectors.toList());

        return PedidoResponse.builder()
                .id(pedido.getId())
                .clienteId(pedido.getCliente() != null ? pedido.getCliente().getId() : null)
                .clienteNombre(pedido.getCliente() != null ? pedido.getCliente().getNombre() : null)
                .repartidorId(pedido.getRepartidor() != null ? pedido.getRepartidor().getId() : null)
                .repartidorNombre(pedido.getRepartidor() != null ? pedido.getRepartidor().getNombre() : null)
                .fechaPedido(pedido.getFechaPedido())
                .costoEnvio(pedido.getCostoEnvio())
                .montoTotal(pedido.getMontoTotal())
                .estado(pedido.getEstado())
                .items(detalleResponses)
                .detalles(detalleResponses)
                .build();
    }
}
