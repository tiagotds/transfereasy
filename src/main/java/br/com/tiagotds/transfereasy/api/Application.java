package br.com.tiagotds.transfereasy.api;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.ApplicationPath;

import org.jboss.resteasy.plugins.interceptors.CorsFilter;

import br.com.tiagotds.transfereasy.api.controller.AccountController;
import br.com.tiagotds.transfereasy.api.controller.CustomerController;
import br.com.tiagotds.transfereasy.api.exception.InvalidEntryJsonHandler;
import br.com.tiagotds.transfereasy.api.exception.InvalidJsonEnumHandler;
import br.com.tiagotds.transfereasy.api.exception.InvalidJsonHandler;
import br.com.tiagotds.transfereasy.api.exception.TransfereasyExceptionHandler;

@ApplicationPath("/api")
public class Application extends javax.ws.rs.core.Application {

	private Set<Object> singletons = new HashSet<Object>();
	private Set<Class<?>> classes = new HashSet<Class<?>>();

	public Application() {
		singletons.add(new CustomerController());
		singletons.add(new AccountController());

		CorsFilter corsFilter = new CorsFilter();
		corsFilter.getAllowedOrigins().add("*");
		singletons.add(corsFilter);

		classes.add(TransfereasyExceptionHandler.class);
		classes.add(InvalidEntryJsonHandler.class);
		classes.add(InvalidJsonHandler.class);
		classes.add(InvalidJsonEnumHandler.class);
	}

	@Override
	public Set<Class<?>> getClasses() {
		return classes;
	}

	@Override
	public Set<Object> getSingletons() {
		return singletons;
	}
}
