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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.jdrupes.eclipse.minify.plugin.MinifyBuilder;
import org.osgi.service.prefs.Preferences;

public class MinifyJsPropertyPage extends MinifyPropertyPage {

	private static final String[][] OPTIONS = new String[][] {
		{ MinifyBuilder.DONT_MINIFY, MinifyBuilder.YUI_COMPRESSOR },
		{ "(none)", "YUI Compressor" }
	};
	
	protected Composite yuiOptGroup;
	protected Button preserveSemicolons;
	protected Button disableOptimizations;
	
	@Override
	protected String[][] options() {
		return OPTIONS;
	}

	@Override
	protected void addSpecificSection(Composite parent, Preferences prefs) {
		yuiOptGroup = new Composite(parent, SWT.BORDER);
		GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		yuiOptGroup.setLayout(layout);
		GridData data = new GridData();
		data.verticalAlignment = GridData.FILL;
		data.horizontalAlignment = GridData.FILL;
		yuiOptGroup.setLayoutData(data);
		
		preserveSemicolons = createCheckbox("Preserve Semicolons", 
				MinifyBuilder.YUI_PRESERVE_SEMICOLONS, true, prefs);
		disableOptimizations = createCheckbox("Disable Optimizations",
				MinifyBuilder.YUI_DISABLE_OPTIMIZATIONS, true, prefs);

		updateOptGroups();
		selection().addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				updateOptGroups();
			}
		});
	}

	private Button createCheckbox(
			String label, String property, boolean defaultValue, Preferences prefs) {
		Button checkbox = new Button(yuiOptGroup, SWT.CHECK);
		checkbox.setText(label);
		checkbox.setSelection(prefs.getBoolean(preferenceKey(property), defaultValue));
		checkbox.requestLayout();
		return checkbox;
	}

	private void updateOptGroups() {
		yuiOptGroup.setVisible(selection().getText().equals(OPTIONS[1][1]));
	}

	@Override
	protected boolean performSpecificOk(Preferences prefs) throws CoreException {
		updateProperty(MinifyBuilder.YUI_PRESERVE_SEMICOLONS, preserveSemicolons, prefs);
		updateProperty(MinifyBuilder.YUI_DISABLE_OPTIMIZATIONS, disableOptimizations, prefs);
		return true;
	}
	
	private void updateProperty(String property, Button checkbox, Preferences prefs)
		throws CoreException {
		prefs.putBoolean(preferenceKey(property), checkbox.getSelection());
	}
}