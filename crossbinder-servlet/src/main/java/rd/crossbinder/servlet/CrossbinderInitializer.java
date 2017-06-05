/*
 * Copyright (c) The original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package rd.crossbinder.servlet;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.Servlet;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.annotation.HandlesTypes;
import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;

import org.apache.commons.lang3.StringUtils;

import rd.classpath.ClasspathBrowser;
import rd.classpath.ScanPath;
import rd.crossbinder.config.jackson.JacksonConfigProvider;
import rd.crossbinder.hod.Crossbinder;

/**
 *
 * @author randondiesel
 *
 */

@HandlesTypes({HttpServlet.class, Filter.class})
public class CrossbinderInitializer implements ServletContainerInitializer {

	private static final Logger LOGGER = Logger.getLogger(CrossbinderInitializer.class.getName());

	public static final String KEY_CROSSBINDER_INST        = "crossbinder.instance";
	public static final String KEY_CROSSBINDER_CONFIG_TYPE = "crossbinder.config.type";
	public static final String KEY_CROSSBINDER_CONFIG_PATH = "crossbinder.config.path";

	@Override
	public void onStartup(Set<Class<?>> classes, ServletContext ctxt) throws ServletException {

		JacksonConfigProvider jcp = createConfigProvider(ctxt);
		if(jcp == null) {
			LOGGER.severe("unable to load crossbinder configuration. Ending initialization.");
			return;
		}
		CrossbinderConfig pgConfig = (CrossbinderConfig) jcp.getValue("crossbinder", CrossbinderConfig.class);
		Crossbinder crossbinder = null;
		try {
			crossbinder = (Crossbinder) ctxt.getAttribute(KEY_CROSSBINDER_INST);
			if(crossbinder != null) {
				LOGGER.fine("crossbinder instance found in servlet context");
			}
			else {
				ScanPath scanp = new ScanPath();
				List<String> corePkgNames = pgConfig.getCorePackageNames();
				for(String name : corePkgNames) {
					scanp.includePackage(name);
				}
				crossbinder = Crossbinder.create();
				crossbinder.scanPath(scanp);
				crossbinder.configure(jcp);
				crossbinder.start();
			}
		}
		catch(Exception exep) {
			LOGGER.log(Level.SEVERE, "unable to prepare crossbinder. Ending initialization.", exep);
			return;
		}

		LOGGER.fine("registering servlets and filters");
		List<String> webPkgNames = pgConfig.getWebPackageNames();

		ClasspathBrowser cpb = new ClasspathBrowser();
		ScanPath scanp = new ScanPath();
		for(String pkgName : webPkgNames) {
			scanp.includePackage(pkgName);
		}
		cpb.load(scanp);

		List<Class<?>> servletTypes = cpb.listAnnotatedClasses(WebServlet.class);
		for(Class<?> type : servletTypes) {
			if(Servlet.class.isAssignableFrom(type)) {
				registerServlet(type, ctxt, crossbinder);
			}
		}

		List<Class<?>> filterTypes = cpb.listAnnotatedClasses(WebFilter.class);
		for(Class<?> type : filterTypes) {
			registerFilter(type, ctxt, crossbinder);
		}
	}

	////////////////////////////////////////////////////////////////////////////
	// Helper methods

	JacksonConfigProvider createConfigProvider(ServletContext ctxt) {
		Class<? extends WebConfiguration> configType = null;
		String configPath = null;
		try {
			String configTypeName = (String) ctxt.getAttribute(KEY_CROSSBINDER_CONFIG_TYPE);
			if(StringUtils.isEmpty(configTypeName)) {
				LOGGER.fine("crossbinder config type not found in servlet context");
				return null;
			}
			configType = Class.forName(configTypeName).asSubclass(WebConfiguration.class);
			configPath = (String) ctxt.getAttribute(KEY_CROSSBINDER_CONFIG_PATH);
			if(StringUtils.isEmpty(configPath)) {
				LOGGER.fine("crossbinder config path not found in servlet context");
				return null;
			}
		}
		catch(Exception exep) {
			LOGGER.log(Level.SEVERE, "error retrieving crossbinder config from servlet context", exep);
			return null;
		}

		JacksonConfigProvider jcp = new JacksonConfigProvider();
		try {
			File configFile = new File(ctxt.getRealPath(configPath));
			FileInputStream input = new FileInputStream(configFile);
			if(StringUtils.endsWithAny(configPath, ".yaml", ".yml")) {
				jcp.loadYaml(configType, input);
			}
			else if(StringUtils.endsWithIgnoreCase(configPath, ".json")) {
				jcp.loadJson(configType, input);
			}
		}
		catch(Exception exep) {
			LOGGER.log(Level.SEVERE, "error loading crossbinder config.", exep);
			return null;
		}
		return jcp;
	}

	private void registerServlet(Class<?> type, ServletContext ctxt, Crossbinder crossbinder) {
		LOGGER.fine(String.format("registering servlet: %s", type.getName()));
		WebServlet ann = type.getAnnotation(WebServlet.class);
		if(ann == null) {
			LOGGER.warning(String.format("servlet %s: not annotated with @WebServlet", type.getName()));
			return;
		}
		String servletName = ann.name();
		if(StringUtils.isBlank(servletName)) {
			LOGGER.warning(String.format("servlet %s: name could not be blank", type.getName()));
			return;
		}
		String[] patterns = ann.urlPatterns();
		if(patterns == null || patterns.length == 0) {
			patterns = ann.value();
			if(patterns == null || patterns.length == 0) {
				LOGGER.warning(String.format("servlet %s: missing url patterns", type.getName()));
				return;
			}
		}
		int losu = ann.loadOnStartup();
		Servlet servlet = null;
		try {
			servlet = (Servlet) type.newInstance();
			crossbinder.injector().inject(servlet);
		}
		catch(Exception exep) {
			LOGGER.warning(String.format("error creating servlet: %s", type.getName()));
			return;
		}

		ServletRegistration.Dynamic dynamic = ctxt.addServlet(servletName, servlet);
		dynamic.addMapping(patterns);
		dynamic.setLoadOnStartup(losu);
		if(ann.initParams() == null) {
			return;
		}
		for(WebInitParam param : ann.initParams()) {
			String name = param.name();
			String value = param.value();
			if(StringUtils.isNoneBlank(name)) {
				dynamic.setInitParameter(name, value);
			}
		}
	}

	private void registerFilter(Class<?> type, ServletContext ctxt, Crossbinder crossbinder) {
		LOGGER.fine(String.format("registering filter: %s", type.getName()));
		WebFilter ann = type.getAnnotation(WebFilter.class);
		if(ann == null) {
			LOGGER.warning(String.format("filter %s: not annotated with @WebFilter", type.getName()));
			return;
		}
		String filterName = ann.filterName();
		if(StringUtils.isBlank(filterName)) {
			LOGGER.warning(String.format("filter %s: name could not be blank", type.getName()));
			return;
		}

		String[] patterns = ann.urlPatterns();
		if(patterns == null || patterns.length == 0) {
			patterns = ann.value();
		}
		String[] servletNames = ann.servletNames();
		if((patterns == null || patterns.length == 0) && (servletNames == null || servletNames.length == 0)) {
			LOGGER.warning(String.format("filter %s: missing url patterns or servlet names",
					type.getName()));
			return;
		}

		EnumSet<DispatcherType> dispatchers = null;
		DispatcherType[] dtypes = ann.dispatcherTypes();
		if(dtypes != null && dtypes.length > 0) {
			dispatchers = EnumSet.copyOf(Arrays.asList(dtypes));
		}

		Filter filter = null;
		try {
			filter = (Filter) type.newInstance();
			crossbinder.injector().inject(filter);
		}
		catch(Exception exep) {
			LOGGER.warning(String.format("error creating filter %s", type.getName()));
			return;
		}
		FilterRegistration.Dynamic dynamic = ctxt.addFilter(filterName, filter);
		dynamic.addMappingForUrlPatterns(dispatchers, true, patterns);
		dynamic.addMappingForServletNames(dispatchers, true, servletNames);

		if(ann.initParams() == null) {
			return;
		}
		for(WebInitParam param : ann.initParams()) {
			String name = param.name();
			String value = param.value();
			if(StringUtils.isNoneBlank(name)) {
				dynamic.setInitParameter(name, value);
			}
		}
	}
}
