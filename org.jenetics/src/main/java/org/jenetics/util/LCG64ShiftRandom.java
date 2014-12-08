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
package org.jenetics.util;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.jenetics.internal.util.Equality.eq;

import java.io.Serializable;

import org.jenetics.internal.math.arithmetic;
import org.jenetics.internal.math.random;
import org.jenetics.internal.util.Equality;
import org.jenetics.internal.util.Hash;

/**
 * This class implements a linear congruential PRNG with additional bit-shift
 * transition. The base recursion
 * <p>
 * <img
 *     alt="r_{i+1} = (a\cdot r_i + b) \mod 2^{64}"
 *     src="doc-files/lcg-recursion.gif"
 * >
 * </p>
 * is followed by a non-linear transformation
 * <p>
 * <img
 *     alt="\begin{eqnarray*}
 *           t &=& r_i                \\
 *           t &=& t \oplus (t >> 17) \\
 *           t &=& t \oplus (t << 31) \\
 *           t &=& t \oplus (t >> 8)
 *         \end{eqnarray*}"
 *     src="doc-files/lcg-non-linear.gif"
 * >
 * </p>
 * which destroys the lattice structure introduced by the recursion. The period
 * of this PRNG is 2<sup>64</sup>, {@code iff} <i>b</i> is odd and <i>a</i>
 * {@code mod} 4 = 1.
 * <p>
 *
 * <em>
 * This is an re-implementation of the
 * <a href="https://github.com/rabauke/trng4/blob/master/src/lcg64_shift.hpp">
 * trng::lcg64_shift</a> PRNG class of the
 * <a href="http://numbercrunch.de/trng/">TRNG</a> library created by Heiko
 * Bauke.</em>
 *
 * <p>
 * <strong>Not that the base implementation of the {@code LCG64ShiftRandom}
 * class is not thread-safe.</strong> If multiple threads requests random
 * numbers from this class, it <i>must</i> be synchronized externally.
 * Alternatively you can use the thread-safe implementations
 * {@link LCG64ShiftRandom.ThreadSafe} or {@link LCG64ShiftRandom.ThreadLocal}.
 *
 * @see <a href="http://numbercrunch.de/trng/">TRNG</a>
 * @see RandomRegistry
 *
 * @author <a href="mailto:franz.wilhelmstoetter@gmx.at">Franz Wilhelmstötter</a>
 * @since 1.1
 * @version 2.0 &mdash; <em>$Date$</em>
 */
public class LCG64ShiftRandom extends Random64 {

	private static final long serialVersionUID = 1L;

	/**
	 * This class represents a <i>thread local</i> implementation of the
	 * {@code LCG64ShiftRandom} PRNG.
	 *
	 * It's recommended to initialize the {@code RandomRegistry} the following
	 * way:
	 *
	 * [code]
	 * // Register the PRNG with the default parameters.
	 * RandomRegistry.setRandom(new LCG64ShiftRandom.ThreadLocal());
	 *
	 * // Register the PRNG with the {@code LECUYER3} parameters.
	 * RandomRegistry.setRandom(new LCG64ShiftRandom.ThreadLocal(
	 *     LCG64ShiftRandom.LECUYER3
	 * ));
	 * [/code]
	 *
	 * Be aware, that calls of the {@code setSeed(long)} method will throw an
	 * {@code UnsupportedOperationException} for <i>thread local</i> instances.
	 * [code]
	 * RandomRegistry.setRandom(new LCG64ShiftRandom.ThreadLocal());
	 *
	 * // Will throw 'UnsupportedOperationException'.
	 * RandomRegistry.getRandom().setSeed(1234);
	 * [/code]
	 *
	 * @author <a href="mailto:franz.wilhelmstoetter@gmx.at">Franz Wilhelmstötter</a>
	 * @since 1.1
	 * @version 3.0 &mdash; <em>$Date$</em>
	 */
	public static final class ThreadLocal
		extends java.lang.ThreadLocal<LCG64ShiftRandom>
	{
		private static final long STEP_BASE = 1L << 56;

		private int _block = 0;
		private long _seed = random.seed();

		private final Param _param;

		/**
		 * Create a new <i>thread local</i> instance of the
		 * {@code LCG64ShiftRandom} PRGN with the {@code DEFAULT} parameters.
		 */
		public ThreadLocal() {
			this(Param.DEFAULT);
		}

		/**
		 * Create a new <i>thread local</i> instance of the
		 * {@code LCG64ShiftRandom} PRGN with the given parameters.
		 *
		 * @param param the LC parameters.
		 * @throws NullPointerException if the given parameters are null.
		 */
		public ThreadLocal(final Param param) {
			_param = requireNonNull(param, "PRNG param must not be null.");
		}

		/**
		 * Create a new PRNG using <i>block splitting</i> for guaranteeing well
		 * distributed PRN for every thread.
		 *
		 * <p>
		 * <strong>Tina’s Random Number Generator Library</strong>
		 * <br>
		 * <em>Chapter 2. Pseudo-random numbers for parallel Monte Carlo
		 *     simulations, Page 7</em>
		 * <br>
		 * <small>Heiko Bauke</small>
		 * <br>
		 * [<a href="http://numbercrunch.de/trng/trng.pdf">
		 *  http://numbercrunch.de/trng/trng.pdf
		 *  </a>].
		 */
		@Override
		protected synchronized LCG64ShiftRandom initialValue() {
			if (_block > 127) {
				_block = 0;
				_seed = random.seed();
			}

			final LCG64ShiftRandom random = new TLLCG64ShiftRandom(_seed, _param);
			random.jump((_block++)*STEP_BASE);
			return random;
		}

	}

	private static final class TLLCG64ShiftRandom extends LCG64ShiftRandom {

		private static final long serialVersionUID = 1L;

		private final Boolean _sentry = Boolean.TRUE;

		private TLLCG64ShiftRandom(final long seed, final Param param) {
			super(param, seed);
		}

		@Override
		public void setSeed(final long seed) {
			if (_sentry != null) {
				throw new UnsupportedOperationException(
					"The 'setSeed(long)' method is not supported " +
					"for thread local instances."
				);
			}
		}

	}

	/**
	 * This is a <i>thread safe</i> variation of the this PRGN&mdash;by
	 * synchronizing the random number generation.
	 *
	 * @author <a href="mailto:franz.wilhelmstoetter@gmx.at">Franz Wilhelmstötter</a>
	 * @since 1.1
	 * @version 3.0 &mdash; <em>$Date$</em>
	 */
	public static final class ThreadSafe extends LCG64ShiftRandom {
		private static final long serialVersionUID = 1L;

		/**
		 * Create a new PRNG instance with the given parameter and seed.
		 *
		 * @param seed the seed of the PRNG.
		 * @param param the parameter of the PRNG.
		 * @throws NullPointerException if the given {@code param} is null.
		 */
		public ThreadSafe(final long seed, final Param param) {
			super(param, seed);
		}

		/**
		 * Create a new PRNG instance with {@link Param#DEFAULT} parameter and
		 * the given seed.
		 *
		 * @param seed the seed of the PRNG
		 */
		public ThreadSafe(final long seed) {
			this(seed, Param.DEFAULT);
		}

		/**
		 * Create a new PRNG instance with the given parameter and a safe
		 * default seed.
		 *
		 * @param param the PRNG parameter.
		 * @throws NullPointerException if the given {@code param} is null.
		 */
		public ThreadSafe(final Param param) {
			this(random.seed(), param);
		}

		/**
		 * Create a new PRNG instance with {@link Param#DEFAULT} parameter and
		 * a safe seed.
		 */
		public ThreadSafe() {
			this(random.seed(), Param.DEFAULT);
		}

		@Override
		public synchronized void setSeed(final long seed) {
			super.setSeed(seed);
		}

		@Override
		public synchronized long nextLong() {
			return super.nextLong();
		}

		@Override
		public synchronized void split(final int p, final int s) {
			super.split(p, s);
		}

		@Override
		public synchronized void jump2(final int s) {
			super.jump2(s);
		}

		@Override
		public synchronized void jump(final long step) {
			super.jump(step);
		}

	}

	/**
	 * Parameter class for the {@code LCG64ShiftRandom} generator, for the
	 * parameters <i>a</i> and <i>b</i> of the LC recursion
	 * <i>r<sub>i+1</sub> = a · r<sub>i</sub> + b</i> mod <i>2<sup>64</sup></i>.
	 *
	 * @author <a href="mailto:franz.wilhelmstoetter@gmx.at">Franz Wilhelmstötter</a>
	 * @since 1.1
	 * @version 2.0 &mdash; <em>$Date$</em>
	 */
	public static final class Param implements Serializable {

		private static final long serialVersionUID = 1L;

		/**
		 * The default PRNG parameters: a = 0xFBD19FBBC5C07FF5L; b = 1
		 */
		public static final Param DEFAULT = Param.of(0xFBD19FBBC5C07FF5L, 1L);

		/**
		 * LEcuyer 1 parameters: a = 0x27BB2EE687B0B0FDL; b = 1
		 */
		public static final Param LECUYER1 = Param.of(0x27BB2EE687B0B0FDL, 1L);

		/**
		 * LEcuyer 2 parameters: a = 0x2C6FE96EE78B6955L; b = 1
		 */
		public static final Param LECUYER2 = Param.of(0x2C6FE96EE78B6955L, 1L);

		/**
		 * LEcuyer 3 parameters: a = 0x369DEA0F31A53F85L; b = 1
		 */
		public static final Param LECUYER3 = Param.of(0x369DEA0F31A53F85L, 1L);


		/**
		 * The parameter <i>a</i> of the LC recursion.
		 */
		public final long a;

		/**
		 * The parameter <i>b</i> of the LC recursion.
		 */
		public final long b;

		/**
		 * Create a new parameter object.
		 *
		 * @param a the parameter <i>a</i> of the LC recursion.
		 * @param b the parameter <i>b</i> of the LC recursion.
		 */
		private Param(final long a, final long b) {
			this.a = a;
			this.b = b;
		}

		public static Param of(final long a, final long b) {
			return new Param(a, b);
		}

		@Override
		public int hashCode() {
			return 31*(int)(a^(a >>> 32)) + 31*(int)(b^(b >>> 32));
		}

		@Override
		public boolean equals(final Object obj) {
			return Equality.of(this, obj).test(p -> a == p.a && b == p.b);
		}

		@Override
		public String toString() {
			return format("%s[a=%d, b=%d]", getClass().getName(), a, b);
		}
	}

	/**
	 * Represents the state of this random engine
	 */
	private final static class State implements Serializable {
		private static final long serialVersionUID = 1L;

		long _r;

		State(final long seed) {
			setSeed(seed);
		}

		void setSeed(final long seed) {
			_r = seed;
		}

		@Override
		public int hashCode() {
			return Hash.of(getClass()).and(_r).value();
		}

		@Override
		public boolean equals(final Object obj) {
			return Equality.of(this, obj).test(state -> state._r == _r);
		}

		@Override
		public String toString() {
			return format("State[%d]", _r);
		}
	}


	private Param _param;
	private final State _state;

	/**
	 * Create a new PRNG instance with the given parameter and seed.
	 *
	 * @param param the parameter of the PRNG.
	 * @param seed the seed of the PRNG.
	 * @throws NullPointerException if the given {@code param} is null.
	 */
	public LCG64ShiftRandom(final Param param, final long seed) {
		_param = requireNonNull(param, "PRNG param must not be null.");
		_state = new State(seed);
	}

	/**
	 * Create a new PRNG instance with the given parameter and a safe seed
	 *
	 * @param param the PRNG parameter.
	 * @throws NullPointerException if the given {@code param} is null.
	 */
	public LCG64ShiftRandom(final Param param) {
		this(param, random.seed());
	}

	/**
	 * Create a new PRNG instance with {@link Param#DEFAULT} parameter and the
	 * given seed.
	 *
	 * @param seed the seed of the PRNG
	 */
	public LCG64ShiftRandom(final long seed) {
		this(Param.DEFAULT, seed);
	}

	/**
	 * Create a new PRNG instance with {@link Param#DEFAULT} parameter and safe
	 * seed.
	 */
	public LCG64ShiftRandom() {
		this(Param.DEFAULT, random.seed());
	}

	@Override
	public long nextLong() {
		step();

		long t = _state._r;
		t ^= t >>> 17;
		t ^= t << 31;
		t ^= t >>> 8;
		return t;
	}

	private void step() {
		_state._r = _param.a*_state._r + _param.b;
	}

	@Override
	public void setSeed(final long seed) {
		if (_state != null) _state.setSeed(seed);
	}

	/**
	 * Changes the internal state of the PRNG in a way that future calls to
	 * {@link #nextLong()} will generated the s<sup>th</sup> sub-stream of
	 * p<sup>th</sup> sub-streams. <i>s</i> must be within the range of
	 * {@code [0, p-1)}. This method is mainly used for <i>parallelization</i>
	 * via <i>leap-frogging</i>.
	 *
	 * @param p the overall number of sub-streams
	 * @param s the s<sup>th</sup> sub-stream
	 * @throws IllegalArgumentException if {@code p < 1 || s >= p}.
	 */
	public void split(final int p, final int s) {
		if (p < 1) {
			throw new IllegalArgumentException(format(
				"p must be >= 1 but was %d.", p
			));
		}
		if (s >= p) {
			throw new IllegalArgumentException(format(
				"s must be < %d but was %d.", p, s
			));
		}

		if (p > 1) {
			jump(s + 1);
			final long b = _param.b*f(p, _param.a);
			final long a = arithmetic.pow(_param.a, p);
			_param = Param.of(a, b);
			backward();
		}
	}

	/**
	 * Changes the internal state of the PRNG in such a way that the engine
	 * <i>jumps</i> 2<sup>s</sup> steps ahead.
	 *
	 * @param s the 2<sup>s</sup> steps to jump ahead.
	 * @throws IllegalArgumentException if {@code s < 0}.
	 */
	public void jump2(final int s) {
		if (s < 0) {
			throw new IllegalArgumentException(format(
				"s must be positive but was %d.", s
			));
		}

		if (s >= Long.SIZE) {
			throw new IllegalArgumentException(format(
				"The 'jump2' size must be smaller than %d but was %d.",
				Long.SIZE, s
			));
		}

		_state._r = _state._r*arithmetic.pow(_param.a, 1L << s) +
					f(1L << s, _param.a)*_param.b;
	}

	/**
	 * Changes the internal state of the PRNG in such a way that the engine
	 * <i>jumps</i> s steps ahead.
	 *
	 * @param step the steps to jump ahead.
	 * @throws IllegalArgumentException if {@code s < 0}.
	 */
	public void jump(final long step) {
		if (step < 0) {
			throw new IllegalArgumentException(format(
				"step must be positive but was %d", step
			));
		}

		if (step < 16) {
			for (int i = 0; i < step; ++i) {
				step();
			}
		} else {
			long s = step;
			int i = 0;
			while (s > 0) {
				if (s%2 == 1) {
					jump2(i);
				}
				++i;
				s >>= 1;
			}
		}
	}

	private void backward() {
		for (int i = 0; i < Long.SIZE; ++i) {
			jump2(i);
		}
	}

	public Param getParam() {
		return _param;
	}

	@Override
	public int hashCode() {
		return Hash.of(getClass())
			.and(_param)
			.and(_state).value();
	}

	@Override
	public boolean equals(final Object obj) {
		return Equality.of(this, obj).test(random ->
			eq(_param, random._param) &&
			eq(_state, random._state)
		);
	}

	@Override
	public String toString() {
		return format("%s[%s, %s]", getClass().getSimpleName(), _param, _state);
	}



	/* *************************************************************************
	 * Some static helper methods
	 ***************************************************************************/

	/**
	 * Compute prod(1+a^(2^i), i=0..l-1).
	 */
	private static long g(final int l, final long a) {
		long p = a;
		long res = 1;
		for (int i = 0; i < l; ++i) {
			res *= 1 + p;
			p *= p;
		}

		return res;
	}

	/**
	 * Compute sum(a^i, i=0..s-1).
	 */
	private static long f(final long s, final long a) {
		long y = 0;

		if (s != 0) {
			long e = log2Floor(s);
			long p = a;

			for (int l = 0; l <= e; ++l) {
				if (((1L << l) & s) != 0) {
					y = g(l, a) + p*y;
				}
				p *= p;
			}
		}

		return y;
	}

	private static long log2Floor(final long s) {
		long x = s;
		long y = 0;

		while (x != 0) {
			x >>>= 1;
			++y;
		}

		return y - 1;
	}

}

/*
#=============================================================================#
# Testing: org.jenetics.util.LCG64ShiftRandom (2014-08-17 20:23)              #
#=============================================================================#
#=============================================================================#
# Linux 3.13.0-34-generic (amd64)                                             #
# java version "1.8.0_11"                                                     #
# Java(TM) SE Runtime Environment (build 1.8.0_11-b12)                        #
# Java HotSpot(TM) 64-Bit Server VM (build 25.11-b03)                         #
#=============================================================================#
#=============================================================================#
#            dieharder version 3.31.1 Copyright 2003 Robert G. Brown          #
#=============================================================================#
   rng_name    |rands/second|   Seed   |
stdin_input_raw|  3.28e+07  | 151297925|
#=============================================================================#
        test_name   |ntup| tsamples |psamples|  p-value |Assessment
#=============================================================================#
   diehard_birthdays|   0|       100|     100|0.70127033|  PASSED
      diehard_operm5|   0|   1000000|     100|0.90568212|  PASSED
  diehard_rank_32x32|   0|     40000|     100|0.69660120|  PASSED
    diehard_rank_6x8|   0|    100000|     100|0.46570202|  PASSED
   diehard_bitstream|   0|   2097152|     100|0.82715923|  PASSED
        diehard_opso|   0|   2097152|     100|0.46504358|  PASSED
        diehard_oqso|   0|   2097152|     100|0.68201549|  PASSED
         diehard_dna|   0|   2097152|     100|0.87460618|  PASSED
diehard_count_1s_str|   0|    256000|     100|0.96663132|  PASSED
diehard_count_1s_byt|   0|    256000|     100|0.73985861|  PASSED
 diehard_parking_lot|   0|     12000|     100|0.54459758|  PASSED
    diehard_2dsphere|   2|      8000|     100|0.01878920|  PASSED
    diehard_3dsphere|   3|      4000|     100|0.60756380|  PASSED
     diehard_squeeze|   0|    100000|     100|0.21701831|  PASSED
        diehard_sums|   0|       100|     100|0.35118859|  PASSED
        diehard_runs|   0|    100000|     100|0.39087958|  PASSED
        diehard_runs|   0|    100000|     100|0.99497723|  PASSED
       diehard_craps|   0|    200000|     100|0.72732540|  PASSED
       diehard_craps|   0|    200000|     100|0.73969256|  PASSED
 marsaglia_tsang_gcd|   0|  10000000|     100|0.99577648|   WEAK
 marsaglia_tsang_gcd|   0|  10000000|     100|0.33226259|  PASSED
         sts_monobit|   1|    100000|     100|0.71640056|  PASSED
            sts_runs|   2|    100000|     100|0.96463500|  PASSED
          sts_serial|   1|    100000|     100|0.79967738|  PASSED
          sts_serial|   2|    100000|     100|0.40779403|  PASSED
          sts_serial|   3|    100000|     100|0.48027173|  PASSED
          sts_serial|   3|    100000|     100|0.87789765|  PASSED
          sts_serial|   4|    100000|     100|0.06466650|  PASSED
          sts_serial|   4|    100000|     100|0.26350571|  PASSED
          sts_serial|   5|    100000|     100|0.01042120|  PASSED
          sts_serial|   5|    100000|     100|0.56119626|  PASSED
          sts_serial|   6|    100000|     100|0.05455437|  PASSED
          sts_serial|   6|    100000|     100|0.69805614|  PASSED
          sts_serial|   7|    100000|     100|0.11355539|  PASSED
          sts_serial|   7|    100000|     100|0.19653078|  PASSED
          sts_serial|   8|    100000|     100|0.43044940|  PASSED
          sts_serial|   8|    100000|     100|0.99743582|   WEAK
          sts_serial|   9|    100000|     100|0.14028254|  PASSED
          sts_serial|   9|    100000|     100|0.93288088|  PASSED
          sts_serial|  10|    100000|     100|0.70012568|  PASSED
          sts_serial|  10|    100000|     100|0.18115216|  PASSED
          sts_serial|  11|    100000|     100|0.81995075|  PASSED
          sts_serial|  11|    100000|     100|0.92179362|  PASSED
          sts_serial|  12|    100000|     100|0.21198589|  PASSED
          sts_serial|  12|    100000|     100|0.40883907|  PASSED
          sts_serial|  13|    100000|     100|0.99998943|   WEAK
          sts_serial|  13|    100000|     100|0.27731604|  PASSED
          sts_serial|  14|    100000|     100|0.95895532|  PASSED
          sts_serial|  14|    100000|     100|0.92607872|  PASSED
          sts_serial|  15|    100000|     100|0.98102023|  PASSED
          sts_serial|  15|    100000|     100|0.44264467|  PASSED
          sts_serial|  16|    100000|     100|0.11828677|  PASSED
          sts_serial|  16|    100000|     100|0.23479246|  PASSED
         rgb_bitdist|   1|    100000|     100|0.29121750|  PASSED
         rgb_bitdist|   2|    100000|     100|0.13086582|  PASSED
         rgb_bitdist|   3|    100000|     100|0.53978249|  PASSED
         rgb_bitdist|   4|    100000|     100|0.72976457|  PASSED
         rgb_bitdist|   5|    100000|     100|0.84666112|  PASSED
         rgb_bitdist|   6|    100000|     100|0.86700768|  PASSED
         rgb_bitdist|   7|    100000|     100|0.13449114|  PASSED
         rgb_bitdist|   8|    100000|     100|0.43557966|  PASSED
         rgb_bitdist|   9|    100000|     100|0.09198300|  PASSED
         rgb_bitdist|  10|    100000|     100|0.93275832|  PASSED
         rgb_bitdist|  11|    100000|     100|0.99846724|   WEAK
         rgb_bitdist|  12|    100000|     100|0.97653300|  PASSED
rgb_minimum_distance|   2|     10000|    1000|0.07502836|  PASSED
rgb_minimum_distance|   3|     10000|    1000|0.93980150|  PASSED
rgb_minimum_distance|   4|     10000|    1000|0.36421153|  PASSED
rgb_minimum_distance|   5|     10000|    1000|0.65659187|  PASSED
    rgb_permutations|   2|    100000|     100|0.14451218|  PASSED
    rgb_permutations|   3|    100000|     100|0.03978405|  PASSED
    rgb_permutations|   4|    100000|     100|0.14779594|  PASSED
    rgb_permutations|   5|    100000|     100|0.93364399|  PASSED
      rgb_lagged_sum|   0|   1000000|     100|0.92615716|  PASSED
      rgb_lagged_sum|   1|   1000000|     100|0.71752493|  PASSED
      rgb_lagged_sum|   2|   1000000|     100|0.73650782|  PASSED
      rgb_lagged_sum|   3|   1000000|     100|0.39696595|  PASSED
      rgb_lagged_sum|   4|   1000000|     100|0.10648323|  PASSED
      rgb_lagged_sum|   5|   1000000|     100|0.88617882|  PASSED
      rgb_lagged_sum|   6|   1000000|     100|0.80219714|  PASSED
      rgb_lagged_sum|   7|   1000000|     100|0.58298033|  PASSED
      rgb_lagged_sum|   8|   1000000|     100|0.11570437|  PASSED
      rgb_lagged_sum|   9|   1000000|     100|0.96835786|  PASSED
      rgb_lagged_sum|  10|   1000000|     100|0.88375454|  PASSED
      rgb_lagged_sum|  11|   1000000|     100|0.23436184|  PASSED
      rgb_lagged_sum|  12|   1000000|     100|0.90527402|  PASSED
      rgb_lagged_sum|  13|   1000000|     100|0.65328513|  PASSED
      rgb_lagged_sum|  14|   1000000|     100|0.27991085|  PASSED
      rgb_lagged_sum|  15|   1000000|     100|0.94416895|  PASSED
      rgb_lagged_sum|  16|   1000000|     100|0.26881918|  PASSED
      rgb_lagged_sum|  17|   1000000|     100|0.14343824|  PASSED
      rgb_lagged_sum|  18|   1000000|     100|0.95727692|  PASSED
      rgb_lagged_sum|  19|   1000000|     100|0.91246877|  PASSED
      rgb_lagged_sum|  20|   1000000|     100|0.66649531|  PASSED
      rgb_lagged_sum|  21|   1000000|     100|0.85659108|  PASSED
      rgb_lagged_sum|  22|   1000000|     100|0.00504570|  PASSED
      rgb_lagged_sum|  23|   1000000|     100|0.46788837|  PASSED
      rgb_lagged_sum|  24|   1000000|     100|0.32576063|  PASSED
      rgb_lagged_sum|  25|   1000000|     100|0.79360864|  PASSED
      rgb_lagged_sum|  26|   1000000|     100|0.77929419|  PASSED
      rgb_lagged_sum|  27|   1000000|     100|0.96637147|  PASSED
      rgb_lagged_sum|  28|   1000000|     100|0.58945172|  PASSED
      rgb_lagged_sum|  29|   1000000|     100|0.90970707|  PASSED
      rgb_lagged_sum|  30|   1000000|     100|0.83767754|  PASSED
      rgb_lagged_sum|  31|   1000000|     100|0.49373095|  PASSED
      rgb_lagged_sum|  32|   1000000|     100|0.10505688|  PASSED
     rgb_kstest_test|   0|     10000|    1000|0.06462017|  PASSED
     dab_bytedistrib|   0|  51200000|       1|0.06877588|  PASSED
             dab_dct| 256|     50000|       1|0.23631898|  PASSED
Preparing to run test 207.  ntuple = 0
        dab_filltree|  32|  15000000|       1|0.20017265|  PASSED
        dab_filltree|  32|  15000000|       1|0.57220491|  PASSED
Preparing to run test 208.  ntuple = 0
       dab_filltree2|   0|   5000000|       1|0.96635425|  PASSED
       dab_filltree2|   1|   5000000|       1|0.25984240|  PASSED
Preparing to run test 209.  ntuple = 0
        dab_monobit2|  12|  65000000|       1|0.80683565|  PASSED
#=============================================================================#
# Summary: PASSED=110, WEAK=4, FAILED=0                                       #
#          235,031.461 MB of random data created with 103.468 MB/sec          #
#=============================================================================#
#=============================================================================#
# Runtime: 0:37:51                                                            #
#=============================================================================#
*/
