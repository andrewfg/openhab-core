/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
/*
 * generated by Xtext 2.12.0
 */
package org.openhab.core.model.rule.ide

import com.google.inject.Guice
import org.openhab.core.model.rule.RulesRuntimeModule
import org.openhab.core.model.rule.RulesStandaloneSetup
import org.openhab.core.model.script.ServiceModule
import org.eclipse.xtext.util.Modules2

/**
 * Initialization support for running Xtext languages as language servers.
 */
class RulesIdeSetup extends RulesStandaloneSetup {

	override createInjector() {
		Guice.createInjector(new ServiceModule(scriptServiceUtil, scriptEngine), Modules2.mixin(new RulesRuntimeModule, new RulesIdeModule))
	}
	
}
