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

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.PropertyPage;
import org.jdrupes.eclipse.minify.plugin.MinifyBuilder;
import org.jdrupes.eclipse.minify.plugin.PrefsAccess;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

public abstract class MinifyPropertyPage extends PropertyPage {

	private static final String PATH_TITLE = "Path:";
	private static final String MINIFIER_TITLE = "&Minifier:";

	private Combo selection;

	protected abstract String[][] options();
	
	protected Preferences builderPreferences() {
		return PrefsAccess.preferences((IResource)getElement());
	}

	protected String preferenceKey(String resourcePreference) {
		return PrefsAccess.preferenceKey((IResource)getElement(), resourcePreference);
	}
	
	private void addFirstSection(Composite parent) {
		Composite composite = createDefaultComposite(parent);

		//Label for path field
		Label pathLabel = new Label(composite, SWT.NONE);
		pathLabel.setText(PATH_TITLE);

		// Path text field
		Text pathValueText = new Text(composite, SWT.WRAP | SWT.READ_ONLY);
		pathValueText.setText(((IResource) getElement()).getFullPath().toString());
	}

	private void addSeparator(Composite parent) {
		Label separator = new Label(parent, SWT.SEPARATOR | SWT.HORIZONTAL);
		GridData gridData = new GridData();
		gridData.horizontalAlignment = GridData.FILL;
		gridData.grabExcessHorizontalSpace = true;
		separator.setLayoutData(gridData);
	}

	private void addSecondSection(Composite parent, Preferences prefs) {
		Composite composite = createDefaultComposite(parent);

		// Label for owner field
		Label ownerLabel = new Label(composite, SWT.NONE);
		ownerLabel.setText(MINIFIER_TITLE);

		// Create a single-selection list
	    selection = new Combo(composite, SWT.READ_ONLY);

	    // Add the items, one by one
	    for (int i = 0; i < options()[0].length; i++) {
	    	selection.add(options()[1][i]);
	    }
		selection.setText(options()[1][0]);

		// Set current selection
		String minifier = prefs.get(preferenceKey(MinifyBuilder.MINIFIER),
				MinifyBuilder.DONT_MINIFY);
		if (!minifier.equals(MinifyBuilder.DONT_MINIFY)) {
			for (int i = 0; i < options()[0].length; i++) {
				if (minifier.equals(options()[0][i])) {
					selection.setText(options()[1][i]);
					break;
				}
			}
		}
	}

	protected Combo selection() {
		return selection;
	}
	
	protected void addSpecificSection(Composite parent, Preferences prefs) {
	}
	
	/**
	 * @see PreferencePage#createContents(Composite)
	 */
	protected Control createContents(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		composite.setLayout(layout);
		GridData data = new GridData(GridData.FILL);
		data.grabExcessHorizontalSpace = true;
		composite.setLayoutData(data);

		Preferences prefs = builderPreferences();
		
		addFirstSection(composite);
		addSeparator(composite);
		addSecondSection(composite, prefs);
		addSpecificSection(composite, prefs);
		
		return composite;
	}

	protected Composite createDefaultComposite(Composite parent) {
		Composite composite = new Composite(parent, SWT.NULL);
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		composite.setLayout(layout);

		GridData data = new GridData();
		data.verticalAlignment = GridData.FILL;
		data.horizontalAlignment = GridData.FILL;
		composite.setLayoutData(data);

		return composite;
	}

	protected void performDefaults() {
		super.performDefaults();
		// Populate the combo with the default value
		selection.setText(options()[1][0]);
	}

	protected boolean performSpecificOk(Preferences prefs) throws CoreException {
		return true;
	}
	
	public boolean performOk() {
		try {
			Preferences prefs = builderPreferences();
			if (!performSpecificOk(prefs)) {
				return false;
			}
		    for (int i = 0; i < options()[0].length; i++) {
		    	if (selection.getText().equals(options()[1][i])) {
					prefs.put(preferenceKey(MinifyBuilder.MINIFIER), options()[0][i]);
		    	}
		    }
		    if (prefs.get(preferenceKey(
		    		MinifyBuilder.MINIFIER), MinifyBuilder.DONT_MINIFY)
		    		.equals(MinifyBuilder.DONT_MINIFY)) {
		    	PrefsAccess.removeResource(prefs, (IResource)getElement());
		    }
			prefs.flush();
			((IResource)getElement()).touch(null);
		} catch (CoreException | BackingStoreException e) {
			return false;
		}
		return true;
	}

}