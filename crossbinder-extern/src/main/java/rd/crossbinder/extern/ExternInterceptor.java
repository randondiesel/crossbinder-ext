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

package rd.crossbinder.extern;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import rd.crossbinder.hod.CrossbinderException;
import rd.crossbinder.hod.LifecycleInterceptor;

/**
 *
 * @author randondiesel
 *
 */

public class ExternInterceptor implements LifecycleInterceptor {

	private static final Logger LOGGER = Logger.getLogger(ExternInterceptor.class.getName());

	private Set<Object>             anonExterns;
	private HashMap<String, Object> namedExterns;

	public ExternInterceptor() {
		anonExterns = new HashSet<>();
		namedExterns = new HashMap<>();
	}

	public void add(Object extern) {
		anonExterns.add(extern);
	}

	public void add(String name, Object extern) {
		namedExterns.put(name, extern);
	}

	////////////////////////////////////////////////////////////////////////////////////////////////
	// Methods of interface LifecycleInterceptor

	@Override
	public void afterCreation(Object target) {
		// NOOP
	}

	@Override
	public void afterInjection(Object target) {
		assignFields(target.getClass(), target);
		Method[] methods = target.getClass().getMethods();
		for(Method method : methods) {
			assignMethod(method, target);
		}
	}

	@Override
	public void afterInitialization(Object target) {
		// NOOP
	}

	@Override
	public void beforeDisposal(Object target) {
		// NOOP
	}

	@Override
	public void afterDisposal(Object target) {
		// NOOP
	}

	////////////////////////////////////////////////////////////////////////////////////////////////
	// Helper methods

	private void assignFields(Class<?> targetCls, Object target) {
		Field[] fields = targetCls.getDeclaredFields();
		for(Field field : fields) {
			External ann = field.getAnnotation(External.class);
			if(ann == null) {
				continue;
			}
			if(Modifier.isStatic(field.getModifiers())) {
				// no injection allowed on static fields.
				LOGGER.warning(String.format("fqcn = %s, field = %s (skip_external_assignment: static field)",
						targetCls.getName(), field.getName()));
				continue;
			}
			Class<?> fieldType = field.getType();
			String name = ann.name().trim().isEmpty() ? ann.value().trim() : ann.name().trim();
			Object fieldValue = null;
			if(name.length() == 0) {
				fieldValue = getExtern(fieldType);
			}
			else {
				fieldValue = getExtern(name, fieldType);
			}
			if(fieldValue == null && !ann.optional()) {
				throw new CrossbinderException("unresolved external assignment for field "
						+ field.getDeclaringClass().getName() + "#" + field.getName()
						+ ": target not found");
			}
			if(fieldValue != null) {
				boolean accessible = field.isAccessible();
				if(!accessible) {
					field.setAccessible(true);
				}
				try {
					field.set(target, fieldValue);
				}
				catch (IllegalArgumentException | IllegalAccessException exep) {
					throw new CrossbinderException("unresolved external assignment for field "
							+ field.getDeclaringClass().getName() + "#" + field.getName()
							+ ": unable to set value", exep);
				}
				if(!accessible) {
					field.setAccessible(false);
				}
			}
		}

		Class<?> superCls = targetCls.getSuperclass();
		if(superCls != null) {
			assignFields(superCls, target);
		}
	}

	private void assignMethod(Method method, Object target) {
		int mod = method.getModifiers();
		if(Modifier.isAbstract(mod) || Modifier.isStatic(mod)) {
			return;
		}
		if(method.getReturnType() != Void.TYPE) {
			return;
		}
		Parameter[] params = method.getParameters();
		if(params.length == 0) {
			return;
		}

		Object[] paramValues = new Object[params.length];
		for(int i=0; i<params.length; i++) {
			if(!params[i].getType().isInterface()) {
				return;
			}
			External ann = params[i].getAnnotation(External.class);
			if(ann == null) {
				return;
			}
			String name = ann.name().trim().isEmpty() ? ann.value().trim() : ann.name().trim();

			Object paramValue = null;
			if(name.length() == 0) {
				paramValue = getExtern(params[i].getType());
			}
			else {
				paramValue = getExtern(name, params[i].getType());
			}

			if(paramValue == null && !ann.optional()) {
				throw new CrossbinderException("unresolved external dependency for method parameter " + method.getName()
						+ "->" + params[i].getName() + ": target not found");
			}

			paramValues[i] = paramValue;
		}

		try {
			method.invoke(target, paramValues);
		}
		catch (IllegalAccessException | IllegalArgumentException
				| InvocationTargetException exep) {
			throw new CrossbinderException("unable to execute method for dependency injection "
				+ method.getDeclaringClass().getName() + ":" + method.getName(), exep);
		}
	}

	private Object getExtern(Class<?> type) {
		for(Object extern : anonExterns) {
			if(type.isAssignableFrom(extern.getClass())) {
				return extern;
			}
		}
		for(Object extern : namedExterns.values()) {
			if(type.isAssignableFrom(extern.getClass())) {
				return extern;
			}
		}
		return null;
	}

	private Object getExtern(String name, Class<?> type) {
		Object extern = namedExterns.get(name);
		if(extern == null) {
			return null;
		}
		if(type.isAssignableFrom(extern.getClass())) {
			return extern;
		}
		return null;
	}
}
