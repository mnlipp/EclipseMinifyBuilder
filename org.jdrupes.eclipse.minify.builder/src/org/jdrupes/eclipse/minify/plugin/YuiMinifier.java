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

package org.jdrupes.eclipse.minify.plugin;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.jdrupes.eclipse.minify.plugin.MinifyBuilder.MinifyRunner;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

/**
 */
public abstract class YuiMinifier extends MinifyRunner {

	public YuiMinifier(MinifyBuilder builder) {
		super(builder);
	}

	/**
	 * The error reporter for the YUICompressor.
	 */
	public class MinifyErrorHandler implements ErrorReporter {

		private IFile file;

		public MinifyErrorHandler(IFile file) {
			this.file = file;
		}

		@Override
		public void error(String message, String sourceName, int line, String lineSource, int lineOffset) {
			builder().addMarker(file, message, line, IMarker.SEVERITY_ERROR);
		}

		@Override
		public void warning(String message, String sourceName, int line, String lineSource, int lineOffset) {
			builder().addMarker(file, message, line, IMarker.SEVERITY_WARNING);
		}

		@Override
		public EvaluatorException runtimeError(String message, String sourceName, int line, String lineSource,
				int lineOffset) {
			return new EvaluatorException(message, sourceName, line, lineSource, lineOffset);
		}
	}

}
