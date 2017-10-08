/*
 * Eclipse Minify Builder
 * Copyright (C) 2017  Michael N. Lipp
 * 
 * This program is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU General Public License as published by 
 * the Free Software Foundation; either version 3 of the License, or 
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License 
 * for more details.
 * 
 * You should have received a copy of the GNU General Public License along 
 * with this program; if not, see <http://www.gnu.org/licenses/>.
 */

package org.jdrupes.eclipse.minify.plugin.properties;

import org.jdrupes.eclipse.minify.plugin.MinifyBuilder;

public class MinifyJsPropertyPage extends MinifyPropertyPage {

	private static final String[][] OPTIONS = new String[][] {
		{ MinifyBuilder.DONT_MINIFY, MinifyBuilder.YUI_COMPRESSOR },
		{ "(none)", "YUI Compressor" }
	};
	
	@Override
	protected String[][] options() {
		return OPTIONS;
	}
	
}