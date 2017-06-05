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

package rd.crossbinder.config.jackson;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import rd.crossbinder.hod.ConfigurationProvider;

/**
 *
 * @author randondiesel
 *
 */

public class JacksonConfigProvider implements ConfigurationProvider {

	private static final Logger LOGGER = Logger.getLogger(JacksonConfigProvider.class.getName());

	private Object configRoot;

	public void loadYaml(Class<?> configRootCls, InputStream input)
			throws JsonParseException, JsonMappingException, IOException {
		YAMLFactory yfac = new YAMLFactory();
		ObjectMapper mapper = new ObjectMapper(yfac);
		configRoot = mapper.readValue(input, configRootCls);
	}

	public void loadJson(Class<?> configRootCls, InputStream input)
			throws JsonParseException, JsonMappingException, IOException {
		JsonFactory jfac = new JsonFactory();
		ObjectMapper mapper = new ObjectMapper(jfac);
		configRoot = mapper.readValue(input, configRootCls);
	}

	////////////////////////////////////////////////////////////////////////////
	// Methods of interface ConfigurationProvider

	@Override
	public boolean contains(String path) {
		LOGGER.fine(String.format("checking for configuration {}", path));
		try {
			return (getValueRecursive(path, configRoot) != null);
		}
		catch (Exception exep) {
			//NOOP
		}
		return false;
	}

	@Override
	public Object getValue(String path, Class<?> type) {
		Object value = null;
		try {
			value = getValueRecursive(path, configRoot);
		}
		catch (Exception exep) {
			exep.printStackTrace();
			return null;
		}
		if(value == null) {
			return null;
		}

		if(type.isAssignableFrom(value.getClass())) {
			return value;
		}
		return null;
	}

	////////////////////////////////////////////////////////////////////////////
	// Helper methods

	private Object getValueRecursive(String path, Object inst) throws Exception {
		String[] parts = path.split("\\.", 2);
		Object value = getValueFromFields(parts[0], inst.getClass(), inst);
		if(value == null) {
			value = getValueFromMethods(parts[0], inst.getClass(), inst);
		}
		if(value != null) {
			if(parts.length > 1) {
				return getValueRecursive(parts[1], value);
			}
			else {
				return value;
			}
		}
		return null;
	}

	private Object getValueFromFields(String path, Class<?> type, Object inst) throws Exception {
		Field[] fields = type.getDeclaredFields();
		for(Field field : fields) {
			JsonProperty ann = field.getAnnotation(JsonProperty.class);
			if(ann != null) {
				String annName = ann.value();
				if(StringUtils.isBlank(annName)) {
					annName = ann.defaultValue();
				}
				if(StringUtils.isBlank(annName)) {
					annName = field.getName();
				}
				if(StringUtils.equals(path, annName)) {
					boolean accessible = field.isAccessible();
					if(!accessible) {
						field.setAccessible(true);
					}
					Object value = field.get(inst);
					if(!accessible) {
						field.setAccessible(false);
					}
					return value;
				}
			}
		}
		Class<?> supCls = type.getSuperclass();
		while(supCls != null) {
			Object value = getValueFromFields(path, supCls, inst);
			if(value != null) {
				return value;
			}
			supCls = supCls.getSuperclass();
		}
		return null;
	}

	private Object getValueFromMethods(String path, Class<?> type, Object inst) throws Exception {
		Method[] methods = type.getDeclaredMethods();
		for(Method method : methods) {
			JsonProperty ann = method.getAnnotation(JsonProperty.class);
			if(ann != null) {
				String annName = ann.value();
				if(StringUtils.isBlank(annName)) {
					annName = ann.defaultValue();
				}
				if(StringUtils.isBlank(annName)) {
					annName = getNameFromMethod(method);
				}
				if(StringUtils.equals(path, annName)) {
					boolean accessible = method.isAccessible();
					if(!accessible) {
						method.setAccessible(true);
					}
					Object value = method.invoke(inst);
					if(!accessible) {
						method.setAccessible(false);
					}
					return value;
				}
			}
		}
		return null;
	}

	private String getNameFromMethod(Method method) {
		if(method.getParameterCount() > 0) {
			return null;
		}
		if(method.getReturnType().equals(Void.TYPE)) {
			return null;
		}

		String mthdName = method.getName();
		if(mthdName.startsWith("get")) {
			if(mthdName.length() <= 3) {
				return null;
			}
			if(method.getReturnType().equals(Boolean.class) || method.getReturnType().equals(Boolean.TYPE)) {
				return null;
			}
			StringBuffer buffer = new StringBuffer(StringUtils.removeStart(mthdName, "get"));
			buffer.setCharAt(0, Character.toLowerCase(buffer.charAt(0)));
			return buffer.toString();
		}
		else if(!mthdName.startsWith("is")) {
			if(mthdName.length() <= 2) {
				return null;
			}
			if(!method.getReturnType().equals(Boolean.class) && !method.getReturnType().equals(Boolean.TYPE)) {
				return null;
			}
			StringBuffer buffer = new StringBuffer(StringUtils.removeStart(mthdName, "is"));
			buffer.setCharAt(0, Character.toLowerCase(buffer.charAt(0)));
			return buffer.toString();
		}
		return null;
	}
}
