/*
 * Java Genetic Algorithm Library (@__identifier__@).
 * Copyright (c) @__year__@ Franz Wilhelmstötter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Author:
 *    Franz Wilhelmstötter (franz.wilhelmstoetter@gmx.at)
 */
package org.jenetics.internal.util;

import static java.util.Objects.requireNonNull;

import java.io.Serializable;
import java.util.function.Supplier;

/**
 * Class for lazy value initialization.
 *
 * @author <a href="mailto:franz.wilhelmstoetter@gmx.at">Franz Wilhelmstötter</a>
 * @since 3.0
 * @version 3.0 &mdash; <em>$Date$</em>
 */
public final class Lazy<T> implements Supplier<T>, Serializable {
	private static final long serialVersionUID = 1L;

	private final Supplier<T> _supplier;

	private T _value;
	private volatile boolean _evaluated = false;

	private Lazy(final Supplier<T> supplier) {
		_supplier = requireNonNull(supplier);
	}

	@Override
	public T get() {
		return _evaluated ? _value : evaluate();
	}

	private synchronized T evaluate() {
		if (!_evaluated) {
			_value = _supplier.get();
			_evaluated = true;
		}

		return _value;
	}

	/**
	 * Create a new lazy value initialization.
	 *
	 * @param supplier the lazy value supplier
	 * @param <T> the value type
	 * @return a new lazy value initialization
	 * @throws java.lang.NullPointerException if the given supplier is
	 *         {@code null}
	 */
	public static <T> Lazy<T> of(final Supplier<T> supplier) {
		return new Lazy<>(supplier);
	}

}
