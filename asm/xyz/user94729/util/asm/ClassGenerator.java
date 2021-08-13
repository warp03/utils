/*
 * Copyright (C) 2021 user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.user94729.util.asm;

/**
 * @see TransformerClassLoader
 */
@FunctionalInterface
public interface ClassGenerator {

	/**
	 * This function is called by a {@link TransformerClassLoader} when a class does not exist.
	 * 
	 * @param className The name of the class that does not exist
	 * @return The new class binary
	 */
	public byte[] generate(String className);
}
