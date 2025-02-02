package com.sinensia.pollosfelices.backend.business.services.impl;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.dozer.DozerBeanMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sinensia.pollosfelices.backend.business.model.EstadoPedido;
import com.sinensia.pollosfelices.backend.business.model.Pedido;
import com.sinensia.pollosfelices.backend.business.model.dtos.Pedido1DTO;
import com.sinensia.pollosfelices.backend.business.services.PedidoServices;
import com.sinensia.pollosfelices.backend.integration.model.EstadoPedidoPL;
import com.sinensia.pollosfelices.backend.integration.model.PedidoPL;
import com.sinensia.pollosfelices.backend.integration.repositories.CamareroPLRepository;
import com.sinensia.pollosfelices.backend.integration.repositories.EstablecimientoPLRepository;
import com.sinensia.pollosfelices.backend.integration.repositories.PedidoPLRepository;

@Service
public class PedidoServicesImpl implements PedidoServices {

	@Autowired
	private PedidoPLRepository pedidoPLRepository;
	
	@Autowired
	private CamareroPLRepository camareroPLRepository;
	
	@Autowired
	private EstablecimientoPLRepository establecimientoPLRepository;
	
	@Autowired
	private DozerBeanMapper mapper;
	
	@Override
	public Long create(Pedido pedido) {
		
		if(pedido.getNumero() != null) {
			throw new IllegalStateException("No se puede crear un pedido que ya tiene número.");
		}
		
		boolean existeCamarero = camareroPLRepository.existsById(pedido.getCamarero().getId());
		boolean existeEstablecimiento = establecimientoPLRepository.existsById(pedido.getEstablecimiento().getCodigo());
		
		if(!existeCamarero) {
			throw new IllegalArgumentException("No se puede crear el pedido. No existe el camarero " + pedido.getCamarero().getId());
		}
		
		if(!existeEstablecimiento) {
			throw new IllegalArgumentException("No se puede crear el pedido. No existe el establecimiento " + pedido.getEstablecimiento().getCodigo());
		}
	
		Long numero = System.currentTimeMillis();
		
		pedido.setNumero(numero);
		pedido.setEstado(EstadoPedido.NUEVO);
		
		PedidoPL pedidoPL = mapper.map(pedido, PedidoPL.class);
		
		pedidoPLRepository.save(pedidoPL);
		
		return numero;
	}

	@Override
	public Optional<Pedido> read(Long numero) {
		Optional<PedidoPL> optionalPL = pedidoPLRepository.findById(numero);
		return optionalPL.isEmpty() ? Optional.empty() : Optional.of(mapper.map(optionalPL.get(), Pedido.class));	
	}

	@Override
	public List<Pedido> getAll() {
		
		return pedidoPLRepository.findAll().stream()
				.map(x -> mapper.map(x, Pedido.class))
				.collect(Collectors.toList());
		            
	}

	@Override
	@Transactional
	public void procesar(Long numero) {

		PedidoPL pedidoPL = getPedidoPL(numero);
		
		EstadoPedidoPL estadoPedidoPL = pedidoPL.getEstado();
		
		if (!estadoPedidoPL.equals(EstadoPedidoPL.NUEVO)) {
			throw new IllegalStateException("No se puede pasar a estado 'EN_PROCESO' desde el estado '" + estadoPedidoPL + "'");
		}
		
		pedidoPLRepository.procesar(numero);
		
	}

	@Override
	@Transactional
	public void entregar(Long numero) {
		
		PedidoPL pedidoPL = getPedidoPL(numero);
		
		EstadoPedidoPL estadoPedidoPL = pedidoPL.getEstado();
		
		if (!estadoPedidoPL.equals(EstadoPedidoPL.EN_PROCESO)) {
			throw new IllegalStateException("No se puede pasar a estado 'PENDIENTE_ENTREGA' desde el estado '" + estadoPedidoPL + "'");
		}
		
		pedidoPLRepository.entregar(numero);
		
	}

	@Override
	@Transactional
	public void servir(Long numero) {
		
		PedidoPL pedidoPL = getPedidoPL(numero);
		
		EstadoPedidoPL estadoPedidoPL = pedidoPL.getEstado();
		
		if (!estadoPedidoPL.equals(EstadoPedidoPL.PENDIENTE_ENTREGA)) {
			throw new IllegalStateException("No se puede pasar a estado 'SERVIDO' desde el estado '" + estadoPedidoPL + "'");
		}
		
		pedidoPLRepository.servir(numero);
		
	}

	@Override
	@Transactional
	public void cancelar(Long numero) {
		
		PedidoPL pedidoPL = getPedidoPL(numero);
		
		EstadoPedidoPL estadoPedidoPL = pedidoPL.getEstado();
		
		if (estadoPedidoPL.equals(EstadoPedidoPL.CANCELADO) || estadoPedidoPL.equals(EstadoPedidoPL.SERVIDO)) {
			throw new IllegalStateException("No se puede pasar a estado 'CANCELADO' desde el estado '" + estadoPedidoPL + "'");
		}
		
		pedidoPLRepository.cancelar(numero);
		
	}
	
	@Override
	public List<Pedido1DTO> getAllPedido1DTO() {
		
		return pedidoPLRepository.findPedido1DTO().stream()
				.map(x -> {
					
					Pedido1DTO pedido1DTO = new Pedido1DTO();
					
					pedido1DTO.setNumero(           (Long) x[0]);
					pedido1DTO.setFecha(            (Date) x[1]);
					pedido1DTO.setEstablecimiento((String) x[2]);
					pedido1DTO.setNombreCamarero( (String) x[3]);
					pedido1DTO.setNombreCliente(  (String) x[4]);
					pedido1DTO.setNumeroLineas(  (Integer) x[5]);
					pedido1DTO.setEstado(((EstadoPedidoPL) x[6]).toString());
					
					return pedido1DTO;})
				
				.collect(Collectors.toList());	
	}
	
	// *********************************************************
	//
	// Private Methods
	//
	// *********************************************************

	private PedidoPL getPedidoPL(Long numero) {
		
		Optional<PedidoPL> optional = pedidoPLRepository.findById(numero);
		
		if (optional.isEmpty()) {
			throw new IllegalArgumentException("No existe el pedido número " + numero);
		}
		
		return optional.get();
	}

}
