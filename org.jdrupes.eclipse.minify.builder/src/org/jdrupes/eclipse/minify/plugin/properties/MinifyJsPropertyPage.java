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
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.jdrupes.eclipse.minify.plugin.MinifyBuilder;
import org.osgi.service.prefs.Preferences;

public class MinifyJsPropertyPage extends MinifyPropertyPage {

	private static final String[][] OPTIONS = new String[][] {
		{ MinifyBuilder.DONT_MINIFY, MinifyBuilder.YUI_COMPRESSOR, MinifyBuilder.GOOGLE_CLOSURE_COMPILER },
		{ "(none)", "YUI Compressor", "Google Closure Compiler" }
	};

	protected Composite optionsStack;
	protected StackLayout optionsStackLayout;
	protected Composite yuiOptGroup;
	protected Button preserveSemicolons;
	protected Button disableOptimizations;
	protected Composite gccOptGroup;
	protected Combo gccOptimization;
	protected String[][] optimizations = new String[][] {
			{ MinifyBuilder.GCC_OPT_WHITESPACE_ONLY, MinifyBuilder.GCC_OPT_SIMPLE, 
				MinifyBuilder.GCC_OPT_ADVANCED },
			{ "Whitespace only", "Simple",
				"Advanced"}
	};
	
	@Override
	protected String[][] options() {
		return OPTIONS;
	}

	@Override
	protected void addSpecificSection(Composite parent, Preferences prefs) {
		optionsStack = new Composite(parent, SWT.BORDER);
		optionsStackLayout = new StackLayout();
		optionsStack.setLayout(optionsStackLayout);
		
		yuiOptGroup = new Composite(optionsStack, SWT.NONE);
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

		gccOptGroup = new Composite(optionsStack, SWT.NONE);
		layout = new GridLayout();
		layout.numColumns = 1;
		gccOptGroup.setLayout(layout);
		data = new GridData();
		data.verticalAlignment = GridData.FILL;
		data.horizontalAlignment = GridData.FILL;
		gccOptGroup.setLayoutData(data);
		
		Composite entry = createDefaultComposite(gccOptGroup);
		// Label for optimization
		Label optLabel = new Label(entry, SWT.NONE);
		optLabel.setText("Optimization:");
		// Create a single-selection list
		gccOptimization = new Combo(entry, SWT.READ_ONLY);

	    // Add the items, one by one
	    for (int i = 0; i < optimizations[0].length; i++) {
	    	gccOptimization.add(optimizations[1][i]);
	    }
	    gccOptimization.setText(optimizations[1][0]);

		// Set current selection
		String optLevel = prefs.get(preferenceKey(MinifyBuilder.GCC_OPTIMIZATION),
				MinifyBuilder.GCC_OPT_WHITESPACE_ONLY);
		for (int i = 0; i < optimizations[0].length; i++) {
			if (optLevel.equals(optimizations[0][i])) {
				gccOptimization.setText(optimizations[1][i]);
				break;
			}
		}
	    
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
		if (selection().getText().equals(OPTIONS[1][0])) {
			optionsStack.setVisible(false);
			return;
		}
		optionsStack.setVisible(true);
		if (selection().getText().equals(OPTIONS[1][1])) {
			optionsStackLayout.topControl = yuiOptGroup;			
		}
		if (selection().getText().equals(OPTIONS[1][2])) {
			optionsStackLayout.topControl = gccOptGroup;			
		}
		optionsStack.layout();
	}

	@Override
	protected boolean performSpecificOk(Preferences prefs) throws CoreException {
		prefs.remove(preferenceKey(MinifyBuilder.YUI_PRESERVE_SEMICOLONS));
		prefs.remove(preferenceKey(MinifyBuilder.YUI_DISABLE_OPTIMIZATIONS));
		prefs.remove(preferenceKey(MinifyBuilder.GCC_OPTIMIZATION));
		if (selection().getText().equals(OPTIONS[1][1])) {
			prefs.putBoolean(preferenceKey(MinifyBuilder.YUI_PRESERVE_SEMICOLONS),
					preserveSemicolons.getSelection());
			prefs.putBoolean(preferenceKey(MinifyBuilder.YUI_DISABLE_OPTIMIZATIONS),
					disableOptimizations.getSelection());
		} else if (selection().getText().equals(OPTIONS[1][2])) {
			for (int i = 0; i < optimizations[0].length; i++) {
				if (gccOptimization.getText().equals(optimizations[1][i])) {
					prefs.put(preferenceKey(MinifyBuilder.GCC_OPTIMIZATION), optimizations[0][i]);
					break;
				}
			}
		}
			
		return true;
	}
}