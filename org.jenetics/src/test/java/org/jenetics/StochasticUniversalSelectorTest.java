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
package org.jenetics;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.jenetics.internal.util.Named;

import org.jenetics.stat.Distribution;
import org.jenetics.stat.Histogram;
import org.jenetics.stat.StatisticsAssert;
import org.jenetics.stat.UniformDistribution;
import org.jenetics.util.Factory;
import org.jenetics.util.LCG64ShiftRandom;
import org.jenetics.util.RandomRegistry;
import org.jenetics.util.Scoped;
import org.jenetics.util.TestData;

/**
 * @author <a href="mailto:franz.wilhelmstoetter@gmx.at">Franz Wilhelmstötter</a>
 * @version <em>$Date$</em>
 */
public class StochasticUniversalSelectorTest
	extends ProbabilitySelectorTester<StochasticUniversalSelector<DoubleGene,Double>>
{

	@Override
	protected boolean isSorted() {
		return true;
	}

	@Override
	protected Factory<StochasticUniversalSelector<DoubleGene, Double>> factory() {
		return StochasticUniversalSelector::new;
	}

	@Override
	protected Distribution<Double> getDistribution() {
		return new UniformDistribution<>(getDomain());
	}

	@Test
	public void selectMinimum() {
		final Function<Genotype<IntegerGene>, Integer> ff = gt ->
			gt.getChromosome().toSeq().stream()
				.mapToInt(IntegerGene::intValue)
				.sum();

		Factory<Genotype<IntegerGene>> gtf =
			Genotype.of(IntegerChromosome.of(0, 100, 10));

		final Population<IntegerGene, Integer> population = IntStream.range(0, 50)
			.mapToObj(i -> Phenotype.of(gtf.newInstance(), ff, 50))
			.collect(Population.toPopulation());

		final StochasticUniversalSelector<IntegerGene, Integer> selector =
			new StochasticUniversalSelector<>();

		final Population<IntegerGene, Integer> selection = selector.select(
			population, 50, Optimize.MINIMUM
		);
	}

	@Override
	@Test
	public void selectDistribution() {
		//throw new SkipException("TODO: implement this test.");
	}

	@Test(dataProvider = "expectedDistribution")
	public void selectDist(final Named<double[]> expected, final Optimize opt) {
		final int npopulation = 100;
		final int loops = 1500;

		try (Scoped<LCG64ShiftRandom> sr = RandomRegistry.scope(new LCG64ShiftRandom())) {
			final Histogram<Double> distribution = SelectorTester.distribution(
				new StochasticUniversalSelector<>(),
				opt,
				npopulation,
				loops
			);

			System.out.println(Arrays.toString(distribution.getNormalizedHistogram()));
			System.out.println(Arrays.toString(expected.value));

			StatisticsAssert.assertDistribution(distribution, expected.value);
		}
	}

	@DataProvider(name = "expectedDistribution")
	public Object[][] expectedDistribution() {
		final String resource =
			"/org/jenetics/selector/distribution/StochasticUniversalSelector";

		return Arrays.asList(Optimize.MAXIMUM).stream()
			.map(opt -> {
				final TestData data = TestData.of(resource, opt.toString());
				final double[] expected = data.stream()
					.map(line -> line[0])
					.mapToDouble(Double::parseDouble)
					.toArray();

				return new Object[]{Named.of("distribution", expected), opt};
			}).toArray(Object[][]::new);
	}

	public static void main(final String[] args) {
		writeDistributionData(Optimize.MINIMUM);
		writeDistributionData(Optimize.MAXIMUM);
	}

	private static void writeDistributionData(final Optimize opt) {
		final ThreadLocal<LCG64ShiftRandom> random = new LCG64ShiftRandom.ThreadLocal();
		try (Scoped<LCG64ShiftRandom> sr = RandomRegistry.scope(random)) {

			// For exact testing
			//final int npopulation = 25_000;
			//final int loops = 2_500_000;

			// For fast testing
			final int npopulation = 5000;
			final int loops = 100_000;

			printDistributions(
				System.out,
				Arrays.asList(""),
				value -> new StochasticUniversalSelector<DoubleGene, Double>(),
				opt,
				npopulation,
				loops
			);
		}
	}

}
