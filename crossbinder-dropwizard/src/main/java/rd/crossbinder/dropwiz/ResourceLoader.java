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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.Path;

import org.apache.commons.lang3.ArrayUtils;

import io.dropwizard.setup.Environment;
import rd.classpath.ClasspathBrowser;
import rd.classpath.ScanPath;
import rd.crossbinder.hod.Crossbinder;

/**
 *
 * @author randondiesel
 *
 * @param <T>
 */

class ResourceLoader<T> {

	private static final Logger LOGGER = Logger.getLogger(ResourceLoader.class.getName());

	private Crossbinder crossbinder;
	private T           config;
	private Environment env;

	public ResourceLoader(Crossbinder cb, T config, Environment env) {
		crossbinder = cb;
		this.config = config;
		this.env = env;
	}

	public void loadAll(List<String> scanPkgNames) {
		Set<Class<?>> resTypes = findResourceTypes(scanPkgNames);
		LOGGER.fine(String.format("found potential resources: %s", resTypes));
		for(Class<?> cls : resTypes) {
			try {
				Object resource = createResource(cls, config, env);
				if(resource != null) {
					LOGGER.fine(String.format("resource created: %s", cls.getName()));
					crossbinder.injector().inject(resource);
					env.jersey().register(resource);
				}
				else {
					LOGGER.warning(String.format("error creating resource: %s", cls.getName()));
				}
			}
			catch(Exception exep) {
				LOGGER.log(Level.WARNING, exep.getMessage(), exep);
			}
		}
	}

	private Set<Class<?>> findResourceTypes(List<String> scanPkgNames) {
		ScanPath scanp = new ScanPath();
		for(String pkgName : scanPkgNames) {
			scanp.includePackage(pkgName);
		}
		ClasspathBrowser cpb = new ClasspathBrowser();
		cpb.load(scanp);

		HashSet<Class<?>> result = new HashSet<>();
		result.addAll(cpb.listAnnotatedClasses(Path.class));
		result.addAll(cpb.listClassesWithAnnotatedMethods(Path.class));
		return result;
	}

	private Constructor<?> findResourceConstructor(Class<?> cls, T config) {
		List<Constructor<?>> ctor2List = new ArrayList<>();
		List<Constructor<?>> ctor1List = new ArrayList<>();
		Constructor<?> defCtor = null;

		Constructor<?>[] ctors = cls.getDeclaredConstructors();
		for(Constructor<?> ctor : ctors) {
			int mod = ctor.getModifiers();
			if(Modifier.isPublic(mod) && !Modifier.isAbstract(mod)) {

				Class<?>[] paramTypes = ctor.getParameterTypes();
				if(paramTypes.length == 2) {
					if(ArrayUtils.contains(paramTypes, config.getClass()) &&
							ArrayUtils.contains(paramTypes, Environment.class)) {
						ctor2List.add(ctor);
					}
				}
				else if(paramTypes.length == 1) {
					if(ArrayUtils.contains(paramTypes, config.getClass()) ||
							ArrayUtils.contains(paramTypes, Environment.class)) {
						ctor1List.add(ctor);
					}
				}
				else if(paramTypes.length == 0) {
					defCtor = ctor;
				}
			}
		}

		if(ctor2List.size() == 1) {
			return ctor2List.get(0);
		}
		if(ctor1List.size() == 1) {
			return ctor1List.get(0);
		}
		return defCtor;
	}

	private Object createResource(Class<?> cls, T config, Environment env) throws InstantiationException,
			IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		Constructor<?> ctor = findResourceConstructor(cls, config);
		if(ctor == null) {
			return null;
		}
		Class<?>[] paramTypes = ctor.getParameterTypes();
		Object[] params = new Object[paramTypes.length];
		for(int i=0; i<paramTypes.length; i++) {
			if(paramTypes[i].equals(config.getClass())) {
				params[i] = config;
			}
			if(paramTypes[i].equals(Environment.class)) {
				params[i] = env;
			}
		}
		return ctor.newInstance(params);
	}
}
