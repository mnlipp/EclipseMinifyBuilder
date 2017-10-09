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

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.ui.IStartup;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

public class Startup implements IStartup {

	@Override
	public void earlyStartup() {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		workspace.addResourceChangeListener(new IResourceChangeListener() {
			
			@Override
			public void resourceChanged(IResourceChangeEvent event) {
				try {
					handleDelta(event.getDelta());
				} catch (BackingStoreException e) {
					e.printStackTrace();
				}
			}
		}, IResourceChangeEvent.PRE_BUILD);
	}

	private void handleDelta(IResourceDelta delta) throws BackingStoreException {
		if (delta.getAffectedChildren().length == 0) {
			if ((delta.getKind() & IResourceDelta.REMOVED) != 0) {
				IResource resource = delta.getResource();
				Preferences resPrefs = MinifyBuilder.preferences(resource);
				if ((delta.getFlags() & IResourceDelta.MOVED_TO) != 0) {
					IPath toPath = delta.getMovedToPath();
					IResource toResource = ResourcesPlugin
							.getWorkspace().getRoot().findMember(toPath);
					MinifyBuilder.moveResource(resPrefs, resource, 
							MinifyBuilder.preferences(toResource), toResource);
				}
				MinifyBuilder.removeResource(resPrefs, resource);
			}
			return;
		}
		for (IResourceDelta child : delta.getAffectedChildren(
				IResourceDelta.CHANGED | IResourceDelta.REMOVED)) {
			handleDelta(child);
		}
	}
}
