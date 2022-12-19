/**
 * Copyright © 2018-2022 Jesse Gallagher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openntf.p2.domino.updatesite.model;

import java.nio.file.Path;
import java.text.MessageFormat;

import lombok.Data;

@Data
public class BundleEmbed {
	private final String name;
	private final Path file;
	
	public BundleEmbed(String name, Path file) {
		this.name = name;
		this.file = file;
	}
	
	@Override
	public String toString() {
		return MessageFormat.format("[{0}: name={1}]", getClass().getSimpleName(), name); //$NON-NLS-1$
	}
}