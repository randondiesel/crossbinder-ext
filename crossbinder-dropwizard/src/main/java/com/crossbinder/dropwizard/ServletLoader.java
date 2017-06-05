/*
 * Copyright (c) The original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.crossbinder.dropwizard;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.Servlet;
import javax.servlet.ServletRegistration;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;

import org.apache.commons.lang3.StringUtils;

import io.dropwizard.setup.Environment;
import rd.classpath.ClasspathBrowser;
import rd.classpath.ScanPath;
import rd.crossbinder.hod.Crossbinder;

/**
 *
 * @author randondiesel
 *
 */

class ServletLoader {

	private static final Logger LOGGER = Logger.getLogger(ServletLoader.class.getName());

	private Crossbinder crossbinder;
	private Environment env;

	public ServletLoader(Crossbinder cb, Environment env) {
		crossbinder = cb;
		this.env = env;
	}

	public void loadAll(List<String> scanPkgNames) {
		List<Class<?>> servletTypes = findServletTypes(scanPkgNames);
		LOGGER.fine(String.format("found potential servlets: %s", servletTypes));
		for(Class<?> type : servletTypes) {
			registerServlet(type);
		}
	}

	private List<Class<?>> findServletTypes(List<String> scanPkgNames) {
		ScanPath scanp = new ScanPath();
		for(String pkgName : scanPkgNames) {
			scanp.includePackage(pkgName);
		}
		ClasspathBrowser cpb = new ClasspathBrowser();
		cpb.load(scanp);

		List<Class<?>> types = cpb.listAnnotatedClasses(WebServlet.class);
		List<Class<?>> result = new ArrayList<>();
		for(Class<?> type : types) {
			if(Servlet.class.isAssignableFrom(type)) {
				result.add(type);
			}
		}
		return result;
	}

	private void registerServlet(Class<?> type) {
		LOGGER.fine(String.format("registering servlet: %s", type.getName()));
		WebServlet ann = type.getAnnotation(WebServlet.class);
		String srvName = ann.name();
		if(StringUtils.isBlank(srvName)) {
			LOGGER.warning(String.format("servlet %s: name could not be blank", type.getName()));
			return;
		}
		String[] patterns = ann.urlPatterns();
		if(patterns == null || patterns.length == 0) {
			patterns = ann.value();
			if(patterns == null || patterns.length == 0) {
				LOGGER.warning(String.format("url patterns missing for servlet %s", type.getName()));
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
			LOGGER.log(Level.WARNING, String.format("error creating servlet %s", type.getName()), exep);
			return;
		}
		ServletRegistration.Dynamic dynamic = env.servlets().addServlet(srvName, servlet);
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
}
