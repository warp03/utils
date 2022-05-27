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
public interface ClassTransformer {

	/**
	 * This function is called by a {@link TransformerClassLoader} when a class can be transformed (class binary edited).
	 * 
	 * @param className The name of the class
	 * @param data      The original class binary
	 * @return The new class binary
	 */
	public byte[] transform(String className, byte[] data);
}
