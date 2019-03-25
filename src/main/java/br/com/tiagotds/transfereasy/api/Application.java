package br.com.tiagotds.transfereasy.api;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.ApplicationPath;

import br.com.tiagotds.transfereasy.api.controller.AccountController;
import br.com.tiagotds.transfereasy.api.controller.CustomerController;

@ApplicationPath("/api")
public class Application extends javax.ws.rs.core.Application {

	private Set<Object> singletons = new HashSet<Object>();
	private Set<Class<?>> empty = new HashSet<Class<?>>();

	public Application() {
		singletons.add(new CustomerController());
		singletons.add(new AccountController());
	}

	@Override
	public Set<Class<?>> getClasses() {
		return empty;
	}

	@Override
	public Set<Object> getSingletons() {
		return singletons;
	}
}
