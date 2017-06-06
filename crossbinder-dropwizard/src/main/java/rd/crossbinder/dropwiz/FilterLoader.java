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

package rd.crossbinder.dropwiz;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebInitParam;

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

class FilterLoader {

	private static final Logger LOGGER = Logger.getLogger(FilterLoader.class.getName());

	private Crossbinder crossbinder;
	private Environment env;

	public FilterLoader(Crossbinder cb, Environment env) {
		crossbinder = cb;
		this.env = env;
	}

	public void loadAll(List<String> scanPkgNames) {
		List<Class<?>> filterTypes = findFilterTypes(scanPkgNames);
		LOGGER.fine(String.format("found potential filters: %s", filterTypes));
		for(Class<?> type : filterTypes) {
			registerFilter(type);
		}
	}

	private List<Class<?>> findFilterTypes(List<String> scanPkgNames) {
		ScanPath scanp = new ScanPath();
		for(String pkgName : scanPkgNames) {
			scanp.includePackage(pkgName);
		}
		ClasspathBrowser cpb = new ClasspathBrowser();
		cpb.load(scanp);

		List<Class<?>> types = cpb.listAnnotatedClasses(WebFilter.class);
		List<Class<?>> result = new ArrayList<>();
		for(Class<?> type : types) {
			if(Filter.class.isAssignableFrom(type)) {
				result.add(type);
			}
		}
		return result;
	}

	private void registerFilter(Class<?> type) {
		LOGGER.fine(String.format("registering filter: %s", type.getName()));
		WebFilter ann = type.getAnnotation(WebFilter.class);
		String filterName = ann.filterName();
		if(StringUtils.isBlank(filterName)) {
			LOGGER.warning(String.format("filter %s: name could not be blank", type.getName()));
			return;
		}

		String[] patterns = ann.urlPatterns();
		if(patterns == null || patterns.length == 0) {
			patterns = ann.value();
		}
		String[] srvNames = ann.servletNames();
		if((patterns == null || patterns.length == 0) && (srvNames == null || srvNames.length == 0)) {
			LOGGER.warning(String.format("filter %s: url patterns or servlet names missing", type.getName()));
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
		FilterRegistration.Dynamic dynamic = env.servlets().addFilter(filterName, filter);
		dynamic.addMappingForUrlPatterns(dispatchers, true, patterns);
		dynamic.addMappingForServletNames(dispatchers, true, srvNames);

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
