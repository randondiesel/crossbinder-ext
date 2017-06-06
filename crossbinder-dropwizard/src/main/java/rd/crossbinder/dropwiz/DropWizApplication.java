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
import java.util.List;
import java.util.logging.Logger;

import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Environment;
import rd.crossbinder.hod.Crossbinder;

/**
 *
 * @author randondiesel
 *
 * @param <T>
 */

public class DropWizApplication<T extends Configuration> extends Application<T> {

	private static final Logger LOGGER = Logger.getLogger(DropWizApplication.class.getName());

	private List<String> scanPkgNames;
	private Crossbinder  crossbinder;
	private boolean      servletFlag;

	public DropWizApplication() {
		scanPkgNames = new ArrayList<>();
	}

	public final DropWizApplication<T> setCrossBinder(Crossbinder cb) {
		crossbinder = cb;
		return this;
	}

	public final DropWizApplication<T> scanPackage(String name) {
		scanPkgNames.add(name);
		return this;
	}

	public final DropWizApplication<T> registerServletsAndFilters() {
		servletFlag = true;
		return this;
	}

	////////////////////////////////////////////////////////////////////////////
	// Methods of base class Application

	@Override
	public final void run(T config, Environment env) throws Exception {

		if(crossbinder == null) {
			throw new RuntimeException("crossbinder not set");
		}

		if(crossbinder.isStarted()) {
			throw new RuntimeException("crossbinder should not be started yet");
		}

		DropWizConfigProvider dcp = new DropWizConfigProvider(config);
		crossbinder.configure(dcp);
		crossbinder.start();

		new ResourceLoader<T>(crossbinder, config, env).loadAll(scanPkgNames);

		if(servletFlag) {
			new ServletLoader(crossbinder, env).loadAll(scanPkgNames);
			new FilterLoader(crossbinder, env).loadAll(scanPkgNames);
		}

		env.lifecycle().manage(new CrossBinderManaged());
		postRun(config, env);
	}

	////////////////////////////////////////////////////////////////////////////
	// Methods to be implemented or used from derived classes

	protected final Crossbinder getCrossbinder() {
		return crossbinder;
	}

	protected void postRun(T config, Environment env) throws Exception {
		//NOOP
	}

	////////////////////////////////////////////////////////////////////////////
	// Inner class for managing CrossBinder

	private class CrossBinderManaged implements Managed {

		public void start() throws Exception {
			/*
			 * NOOP. CrossBinder must already be prepared and started before this
			 * method is called.
			 */
		}

		@Override
		public void stop() throws Exception {
			if(crossbinder != null) {
				crossbinder.stop();
			}
		}
	}
}
