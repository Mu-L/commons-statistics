/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.statistics.descriptive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.statistics.distribution.DoubleTolerances;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test for {@link IntStatistics}.
 *
 * <p>This class verifies that the statistics computed using the summary
 * class are an exact match to the statistics computed individually.
 *
 * <p>For simplicity some tests use only the {@code double} result of the statistic.
 * The full {@link StatisticResult} interface is asserted in the test of the array or
 * stream of input data.
 *
 * <p>Note: Some integer statistics are identical if computed using an array or a stream,
 * e.g. sum, min, mean. However those that use a {@code double} implementation may vary
 * based on input order, e.g. product, skewness. This test computes expected results
 * for array and stream variations for all statistics and does not distinguish these two
 * cases.
 */
class IntStatisticsTest {
    /** Empty statistic array. */
    private static final Statistic[] EMPTY_STATISTIC_ARRAY = {};
    /** The number of random permutations to perform. */
    private static final int RANDOM_PERMUTATIONS = 5;

    /** The test data. */
    private static List<TestData> testData;

    /** The expected result for each statistic on the test data. */
    private static EnumMap<Statistic, List<ExpectedResult>> expectedResult;

    /** The statistics that are co-computed by a single statistic. */
    private static EnumMap<Statistic, EnumSet<Statistic>> coComputed;

    /** The statistics that are co-computed by a combination of statistics. */
    private static Map<EnumSet<Statistic>, EnumSet<Statistic>> coComputedAsCombination;

    /**
     * Container for test data.
     */
    private static class TestData {
        /** Identifier. */
        private final int id;
        /** The sample values. */
        private final int[][] values;
        /** The number of values. */
        private final long size;

        /**
         * @param id Identifier.
         * @param values Sample values.
         */
        TestData(int id, int[]... values) {
            this.id = id;
            this.values = values;
            size = Arrays.stream(values).mapToLong(x -> x.length).sum();
        }

        /**
         * @return the identifier
         */
        int getId() {
            return id;
        }

        /**
         * @return the values as a stream
         */
        Stream<int[]> stream() {
            return Arrays.stream(values);
        }

        /**
         * @return the number of values
         */
        long size() {
            return size;
        }

        /**
         * @return the values as a single array
         */
        int[] toArray() {
            return TestHelper.concatenate(values);
        }
    }

    /**
     * Container for expected results.
     */
    private static class ExpectedResult {
        /** The expected result for a stream of values. */
        private final StatisticResult stream;
        /** The expected result for an array of values. */
        private final StatisticResult array;

        /**
         * @param stream Stream result.
         * @param array Array result.
         */
        ExpectedResult(StatisticResult stream, StatisticResult array) {
            this.stream = stream;
            this.array = array;
        }

        /**
         * @return the expected result for a stream of values.
         */
        StatisticResult getStream() {
            return stream;
        }

        /**
         * @return the expected result for an array of values.
         */
        StatisticResult getArray() {
            return array;
        }
    }

    @BeforeAll
    static void setup() {
        // Random int[] of different lengths, Strictly positive so sum-of-logs is finite.
        final int[][] arrays = IntStream.of(4, 5, 7)
            .mapToObj(i -> ThreadLocalRandom.current().ints(i, 1, Integer.MAX_VALUE).toArray())
            .toArray(int[][]::new);
        // Create test data. IDs must be created in order of the data so use the list size as ID.
        testData = new ArrayList<>();
        testData.add(new TestData(testData.size(), new int[0]));
        testData.add(new TestData(testData.size(), arrays[0]));
        testData.add(new TestData(testData.size(), arrays[1]));
        testData.add(new TestData(testData.size(), arrays[2]));
        testData.add(new TestData(testData.size(), arrays[0], arrays[1]));
        testData.add(new TestData(testData.size(), arrays[1], arrays[2]));
        // Create reference expected results
        expectedResult = new EnumMap<>(Statistic.class);
        addExpected(Statistic.MIN, IntMin::create, IntMin::of);
        addExpected(Statistic.MAX, IntMax::create, IntMax::of);
        addExpected(Statistic.MEAN, IntMean::create, IntMean::of);
        addExpected(Statistic.STANDARD_DEVIATION, IntStandardDeviation::create, IntStandardDeviation::of);
        addExpected(Statistic.VARIANCE, IntVariance::create, IntVariance::of);
        addExpected(Statistic.SKEWNESS,
            () -> DoubleAsIntStatistic.from(Skewness.create()),
            x -> DoubleAsIntStatistic.from(Skewness.of(x)));
        addExpected(Statistic.KURTOSIS,
            () -> DoubleAsIntStatistic.from(Kurtosis.create()),
            x -> DoubleAsIntStatistic.from(Kurtosis.of(x)));
        addExpected(Statistic.PRODUCT,
            () -> DoubleAsIntStatistic.from(Product.create()),
            x -> DoubleAsIntStatistic.from(Product.of(x)));
        addExpected(Statistic.SUM, IntSum::create, IntSum::of);
        addExpected(Statistic.SUM_OF_LOGS,
            () -> DoubleAsIntStatistic.from(SumOfLogs.create()),
            x -> DoubleAsIntStatistic.from(SumOfLogs.of(x)));
        addExpected(Statistic.SUM_OF_SQUARES, IntSumOfSquares::create, IntSumOfSquares::of);
        addExpected(Statistic.GEOMETRIC_MEAN,
            () -> DoubleAsIntStatistic.from(GeometricMean.create()),
            x -> DoubleAsIntStatistic.from(GeometricMean.of(x)));
        // Create co-computed statistics
        coComputed = new EnumMap<>(Statistic.class);
        Arrays.stream(Statistic.values()).forEach(s -> coComputed.put(s, EnumSet.of(s)));
        addCoComputed(Statistic.GEOMETRIC_MEAN, Statistic.SUM_OF_LOGS);
        addCoComputed(Statistic.VARIANCE, Statistic.STANDARD_DEVIATION);
        addCoComputed(Statistic.SUM, Statistic.MEAN);
        // Create statistics computed as part of the main statistic
        addComputedBy(Statistic.VARIANCE, Statistic.SUM, Statistic.MEAN, Statistic.SUM_OF_SQUARES);
        addComputedBy(Statistic.STANDARD_DEVIATION, Statistic.SUM, Statistic.MEAN, Statistic.SUM_OF_SQUARES);
        // Cascade moments up
        coComputed.get(Statistic.KURTOSIS).add(Statistic.SKEWNESS);
        // Combinations
        coComputedAsCombination = new HashMap<>();
        final EnumSet<Statistic> s = EnumSet.of(Statistic.VARIANCE, Statistic.STANDARD_DEVIATION);
        coComputedAsCombination.put(EnumSet.of(Statistic.MEAN, Statistic.SUM_OF_SQUARES), s);
        coComputedAsCombination.put(EnumSet.of(Statistic.SUM, Statistic.SUM_OF_SQUARES), s);
    }

    /**
     * Adds the expected result for the specified {@code statistic}.
     *
     * @param <T> {@link IntStatistic} being computed.
     * @param statistic Statistic.
     * @param constructor Constructor for an empty object.
     * @param arrayConstructor Constructor using an array of values.
     */
    private static <T extends IntStatistic & StatisticAccumulator<T>> void addExpected(Statistic statistic,
            Supplier<T> constructor, Function<int[], T> arrayConstructor) {
        final List<ExpectedResult> results = new ArrayList<>();
        for (final TestData d : testData) {
            // Stream values
            final StatisticResult e1 = d.stream()
                .map(values -> Statistics.add(constructor.get(), values))
                .reduce(StatisticAccumulator::combine)
                .orElseThrow(IllegalStateException::new);
            // Create from array
            final StatisticResult e2 = d.stream()
                .map(arrayConstructor)
                .reduce(StatisticAccumulator::combine)
                .orElseThrow(IllegalStateException::new);
            // Check that there is a finite value to compute during testing
            if (d.size() != 0) {
                assertFinite(e1.getAsDouble(), statistic);
                assertFinite(e2.getAsDouble(), statistic);
            }
            results.add(new ExpectedResult(e1, e2));
        }
        expectedResult.put(statistic, results);
    }

    /**
     * Adds the co-computed statistics to the co-computed mapping.
     * The statistics must be co-computed (computing either one will compute the other)
     * and not a one-way relationship (a computes b but b does not compute a).
     *
     * @param s1 First statistic.
     * @param s2 Second statistic.
     */
    private static void addCoComputed(Statistic s1, Statistic s2) {
        coComputed.get(s1).add(s2);
        coComputed.get(s2).add(s1);
    }

    /**
     * Adds the child statistics that are computed by the parent statistic to the co-computed mapping.
     * The statistics are a one-way relationship (a computes b but b does not compute a).
     *
     * @param parent First statistic.
     * @param children Computed statistics.
     */
    private static void addComputedBy(Statistic parent, Statistic... children) {
        coComputed.get(parent).addAll(EnumSet.of(parent, children));
    }

    @AfterAll
    static void teardown() {
        // Free memory
        testData = null;
        expectedResult = null;
        coComputed = null;
    }

    static Stream<Arguments> streamTestData() {
        final Stream.Builder<Arguments> builder = Stream.builder();
        final Statistic[] statistics = Statistic.values();
        for (int i = 0; i < statistics.length; i++) {
            // Single statistics
            final EnumSet<Statistic> s1 = EnumSet.of(statistics[i]);
            testData.stream().forEach(d -> builder.add(Arguments.of(s1, d)));
            // Paired statistics
            for (int j = i + 1; j < statistics.length; j++) {
                final EnumSet<Statistic> s2 = EnumSet.of(statistics[i], statistics[j]);
                testData.stream().forEach(d -> builder.add(Arguments.of(s2, d)));
            }
        }
        return builder.build();
    }

    /**
     * Test the {@link IntStatistics} when all data is passed as a stream of single values.
     */
    @ParameterizedTest
    @MethodSource(value = {"streamTestData"})
    void testStream(EnumSet<Statistic> stats, TestData data) {
        // Test creation from specified statistics
        final Statistic[] statistics = stats.toArray(EMPTY_STATISTIC_ARRAY);
        assertStatistics(stats, data, x -> acceptAll(statistics, x), ExpectedResult::getStream);
    }

    /**
     * Test the {@link IntStatistics} when data is passed as a {@code int[]} of values.
     */
    @ParameterizedTest
    @MethodSource(value = {"streamTestData"})
    void testArray(EnumSet<Statistic> stats, TestData data) {
        // Test creation from a builder
        final IntStatistics.Builder builder = IntStatistics.builder(stats.toArray(EMPTY_STATISTIC_ARRAY));
        assertStatistics(stats, data, builder::build, ExpectedResult::getArray);
    }

    /**
     * Test the {@link IntStatistics} when data is passed as a range of {@code int[]} of values.
     */
    @ParameterizedTest
    @MethodSource(value = {"streamTestData"})
    final void testArrayRange(EnumSet<Statistic> stats, TestData data) {
        // Test full range
        final int[] values = data.toArray();
        assertStatistics(stats, values, 0, values.length);
        if (values.length == 0) {
            // Nothing more to do
            return;
        }
        // Test half-range
        assertStatistics(stats, values, 0, values.length >> 1);
        assertStatistics(stats, values, values.length >> 1, values.length);
        // Random range
        final long[] seed = TestHelper.createRNGSeed();
        final UniformRandomProvider rng = TestHelper.createRNG(seed);
        for (int repeat = RANDOM_PERMUTATIONS; --repeat >= 0;) {
            final int i = rng.nextInt(values.length);
            final int j = rng.nextInt(values.length);
            assertStatistics(stats, values, Math.min(i, j), Math.max(i, j));
        }
    }

    /**
     * Assert the computed statistics match the expected result.
     *
     * @param stats Statistics that are computed.
     * @param data Test data.
     * @param constructor Constructor to create the {@link IntStatistics}.
     * @param expected Function to obtain the expected result.
     */
    private static void assertStatistics(EnumSet<Statistic> stats, TestData data,
            Function<int[], IntStatistics> constructor,
            Function<ExpectedResult, StatisticResult> expected) {
        final Statistic[] statsArray = stats.toArray(EMPTY_STATISTIC_ARRAY);
        final IntStatistics statistics = data.stream()
            .map(constructor)
            .reduce((a, b) -> combine(statsArray, a, b))
            .orElseThrow(IllegalStateException::new);
        final int id = data.getId();
        Assertions.assertEquals(data.size(), statistics.getCount(), "Count");
        final EnumSet<Statistic> computed = buildComputedStatistics(stats);

        // Test if the statistics are correctly identified as supported
        EnumSet.allOf(Statistic.class).forEach(s -> {
            final boolean isSupported = computed.contains(s);
            Assertions.assertEquals(isSupported, statistics.isSupported(s),
                () -> stats + " isSupported -> " + s.toString());
            if (isSupported) {
                final StatisticResult result = expected.apply(expectedResult.get(s).get(id));
                // Test individual values
                TestHelper.assertDoubleEquals(result, () -> statistics.getAsDouble(s),
                    DoubleTolerances.equals(),
                    () -> stats + " getAsDouble -> " + s.toString());
                TestHelper.assertIntEquals(result, () -> statistics.getAsInt(s),
                    () -> stats + " getAsInt -> " + s.toString());
                TestHelper.assertLongEquals(result, () -> statistics.getAsLong(s),
                    () -> stats + " getAsLong -> " + s.toString());
                TestHelper.assertBigIntegerEquals(result, () -> statistics.getAsBigInteger(s),
                    DoubleTolerances.equals(),
                    () -> stats + " getAsBigInteger -> " + s.toString());
                // Test the values from the result
                TestHelper.assertEquals(result, statistics.getResult(s),
                    DoubleTolerances.equals(),
                    () -> stats + " getResult -> " + s.toString());
            } else {
                Assertions.assertThrows(IllegalArgumentException.class, () -> statistics.getAsDouble(s),
                    () -> stats + " getAsDouble -> " + s.toString());
                Assertions.assertThrows(IllegalArgumentException.class, () -> statistics.getAsInt(s),
                    () -> stats + " getAsInt -> " + s.toString());
                Assertions.assertThrows(IllegalArgumentException.class, () -> statistics.getAsLong(s),
                    () -> stats + " getAsLong -> " + s.toString());
                Assertions.assertThrows(IllegalArgumentException.class, () -> statistics.getAsBigInteger(s),
                    () -> stats + " getAsBigInteger -> " + s.toString());
                Assertions.assertThrows(IllegalArgumentException.class, () -> statistics.getResult(s),
                    () -> stats + " getResult -> " + s.toString());
            }
        });
    }

    /**
     * Assert the computation of the statistics from the specified range of values
     * matches the computation using a copy of the range.
     *
     * @param stats Statistics that are computed.
     * @param data Test data.
     * @param from Inclusive start of the range.
     * @param to Exclusive end of the range.
     */
    private static void assertStatistics(EnumSet<Statistic> stats, int[] data, int from, int to) {
        final IntStatistics expected = IntStatistics.of(stats, Arrays.copyOfRange(data, from, to));
        final IntStatistics statistics = IntStatistics.ofRange(stats, data, from, to);
        Assertions.assertEquals(expected.getCount(), statistics.getCount(), "Count");
        final EnumSet<Statistic> computed = buildComputedStatistics(stats);

        // Test if the statistics are correctly identified as supported
        EnumSet.allOf(Statistic.class).forEach(s -> {
            final boolean isSupported = computed.contains(s);
            Assertions.assertEquals(isSupported, statistics.isSupported(s),
                () -> stats + " isSupported -> " + s.toString());
            if (isSupported) {
                // Test individual values
                Assertions.assertEquals(expected.getAsDouble(s), statistics.getAsDouble(s),
                    () -> stats + " getAsDouble -> " + s.toString());
            }
        });
    }

    /**
     * Builds the complete set of supported statistics from the specified statistics to compute.
     * This method expands the input statistics with co-computed statistics and those derived as
     * combinations of other statistics.
     *
     * @param stats Statistics.
     * @return the statistics
     */
    private static EnumSet<Statistic> buildComputedStatistics(EnumSet<Statistic> stats) {
        final EnumSet<Statistic> computed = EnumSet.copyOf(stats);
        stats.forEach(s -> computed.addAll(coComputed.get(s)));
        // Currently we only have to support pairs here as the test only uses paired stats.
        // Add an assertion to ensure this is fixed in future if required (e.g. using an
        // enumeration of all possible combinations of the input stats as keys into
        // coComputedAsCombination).
        Assertions.assertFalse(stats.size() > 2, "Combinations other than pairs are not supported");
        computed.addAll(coComputedAsCombination.getOrDefault(stats, EnumSet.noneOf(Statistic.class)));
        return computed;
    }

    /**
     * Add all the {@code values} to an aggregator of the {@code statistics}.
     *
     * <p>This method verifies that the {@link IntStatistics#getAsDouble(Statistic)} and
     * {@link IntStatistics#getResult(Statistic)} methods return the same
     * result as values are added.
     *
     * @param statistics Statistics.
     * @param values Values.
     * @return the statistics
     */
    private static IntStatistics acceptAll(Statistic[] statistics, int[] values) {
        final IntStatistics stats = IntStatistics.of(statistics);
        final StatisticResult[] f = getResults(statistics, stats);
        for (final int x : values) {
            stats.accept(x);
            for (int i = 0; i < statistics.length; i++) {
                final Statistic s = statistics[i];
                Assertions.assertEquals(stats.getAsDouble(s), f[i].getAsDouble(),
                    () -> "StatisticResult(" + s + ") after value " + x);
            }
        }
        return stats;
    }

    /**
     * Gets the results for the {@code statistics}.
     *
     * @param statistics Statistics to compute.
     * @param stats Statistic aggregator.
     * @return the results
     */
    private static StatisticResult[] getResults(Statistic[] statistics, final IntStatistics stats) {
        final StatisticResult[] f = new StatisticResult[statistics.length];
        for (int i = 0; i < statistics.length; i++) {
            final StatisticResult supplier = stats.getResult(statistics[i]);
            Assertions.assertFalse(supplier instanceof IntStatistic,
                () -> "IntStatistic instance: " + supplier.getClass().getSimpleName());
            f[i] = supplier;
        }
        return f;
    }

    /**
     * Combine the two statistic aggregators.
     *
     * <p>This method verifies that the {@link IntStatistics#getAsDouble(Statistic)} and
     * {@link IntStatistics#getResult(Statistic)} methods return the same
     * result after the {@link IntStatistics#combine(IntStatistics)}.
     *
     * @param statistics Statistics to compute.
     * @param s1 Statistic aggregator.
     * @param s2 Statistic aggregator.
     * @return the combined IntStatistics
     */
    private static IntStatistics combine(Statistic[] statistics,
        IntStatistics s1, IntStatistics s2) {
        final StatisticResult[] f = getResults(statistics, s1);
        s1.combine(s2);
        for (int i = 0; i < statistics.length; i++) {
            final Statistic s = statistics[i];
            Assertions.assertEquals(s1.getAsDouble(s), f[i].getAsDouble(),
                () -> "StatisticResult(" + s + ") after combine");
        }
        return s1;
    }

    @Test
    void testOfThrows() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> IntStatistics.of());
        Assertions.assertThrows(IllegalArgumentException.class, () -> IntStatistics.of(EMPTY_STATISTIC_ARRAY));
        Assertions.assertThrows(NullPointerException.class, () -> IntStatistics.of(new Statistic[1]));
    }

    @Test
    void testOfSetThrows() {
        final EnumSet<Statistic> s1 = EnumSet.noneOf(Statistic.class);
        Assertions.assertThrows(IllegalArgumentException.class, () -> IntStatistics.of(s1));
        final EnumSet<Statistic> s2 = null;
        Assertions.assertThrows(NullPointerException.class, () -> IntStatistics.of(s2));
        final EnumSet<Statistic> s3 = EnumSet.of(Statistic.MIN);
        final int[] nullValues = null;
        Assertions.assertThrows(NullPointerException.class, () -> IntStatistics.of(s3, nullValues));
    }

    @Test
    void testOfSetRangeThrows() {
        final int[] data = {};
        final EnumSet<Statistic> s1 = EnumSet.noneOf(Statistic.class);
        Assertions.assertThrows(IllegalArgumentException.class, () -> IntStatistics.ofRange(s1, data, 0, data.length));
        final EnumSet<Statistic> s2 = null;
        Assertions.assertThrows(NullPointerException.class, () -> IntStatistics.ofRange(s2, data, 0, data.length));
        final EnumSet<Statistic> s3 = EnumSet.of(Statistic.MIN);
        Assertions.assertThrows(NullPointerException.class, () -> IntStatistics.ofRange(s3, null, 0, 0));
    }

    /**
     * Test the {@link IntStatistics#ofRange(Set, int[], int, int)} method throws with an invalid range.
     */
    @ParameterizedTest
    @MethodSource(value = {"org.apache.commons.statistics.descriptive.TestData#arrayRangeTestData"})
    final void testArrayRangeThrows(int from, int to, int length) {
        final EnumSet<Statistic> statistics = EnumSet.of(Statistic.MIN);
        final int[] values = new int[length];
        Assertions.assertThrows(IndexOutOfBoundsException.class,
            () -> IntStatistics.ofRange(statistics, values, from, to),
            () -> String.format("range [%d, %d) in length %d", from, to, length));
    }

    @Test
    void testBuilderThrows() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> IntStatistics.builder());
        Assertions.assertThrows(IllegalArgumentException.class, () -> IntStatistics.builder(EMPTY_STATISTIC_ARRAY));
        Assertions.assertThrows(NullPointerException.class, () -> IntStatistics.builder(new Statistic[1]));
    }

    @Test
    void testIsSupportedWithNull() {
        final IntStatistics s = IntStatistics.of(Statistic.MIN);
        Assertions.assertThrows(NullPointerException.class, () -> s.isSupported(null));
    }

    @ParameterizedTest
    @MethodSource
    void testIncompatibleCombineThrows(EnumSet<Statistic> stat1, EnumSet<Statistic> stat2) {
        final int[] v1 = {1, 2, 4, 6};
        final int[] v2 = {3, 4, 5};
        final IntStatistics statistics = IntStatistics.of(stat1, v1);
        final IntStatistics other = IntStatistics.of(stat2, v2);
        // Store values
        final double[] values = stat1.stream().mapToDouble(statistics::getAsDouble).toArray();
        Assertions.assertThrows(IllegalArgumentException.class, () -> statistics.combine(other),
            () -> stat1 + " " + stat2);
        // Values should be unchanged
        final int[] i = {0};
        stat1.stream().forEach(
            s -> Assertions.assertEquals(values[i[0]++], statistics.getAsDouble(s), () -> s + " changed"));
    }

    static Stream<Arguments> testIncompatibleCombineThrows() {
        return Stream.of(
            Arguments.of(EnumSet.of(Statistic.MIN), EnumSet.of(Statistic.PRODUCT)),
            Arguments.of(EnumSet.of(Statistic.VARIANCE), EnumSet.of(Statistic.MIN)),
            // Note: SKEWNESS is compatible with KURTOSIS. The combine is not symmetric.
            Arguments.of(EnumSet.of(Statistic.KURTOSIS), EnumSet.of(Statistic.SKEWNESS))
        );
    }

    @ParameterizedTest
    @MethodSource
    void testCompatibleCombine(EnumSet<Statistic> stat1, EnumSet<Statistic> stat2) {
        final int[] v1 = {1, 2, 4, 6};
        final int[] v2 = {3, 4, 5};
        final IntStatistics statistics1 = IntStatistics.of(stat1, v1);
        final IntStatistics statistics2 = IntStatistics.of(stat1, v1);
        // Note: other1 is intentionally using the same flags as statistics1
        final IntStatistics other1 = IntStatistics.of(stat1, v2);
        final IntStatistics other2 = IntStatistics.of(stat2, v2);
        // This should work
        statistics1.combine(other1);
        // This should be compatible
        statistics2.combine(other2);
        // The stats should be the same
        for (final Statistic s : stat1) {
            final double expected = statistics1.getAsDouble(s);
            assertFinite(expected, s);
            Assertions.assertEquals(expected, statistics2.getAsDouble(s), s::toString);
        }
    }

    static Stream<Arguments> testCompatibleCombine() {
        return Stream.of(
            Arguments.of(EnumSet.of(Statistic.MEAN), EnumSet.of(Statistic.VARIANCE)),
            Arguments.of(EnumSet.of(Statistic.VARIANCE), EnumSet.of(Statistic.SUM, Statistic.SUM_OF_SQUARES)),
            Arguments.of(EnumSet.of(Statistic.VARIANCE), EnumSet.of(Statistic.STANDARD_DEVIATION)),
            Arguments.of(EnumSet.of(Statistic.GEOMETRIC_MEAN), EnumSet.of(Statistic.SUM_OF_LOGS)),
            // Compatible combinations
            Arguments.of(EnumSet.of(Statistic.VARIANCE, Statistic.MIN, Statistic.SKEWNESS),
                         EnumSet.of(Statistic.KURTOSIS, Statistic.SUM_OF_SQUARES, Statistic.MEAN, Statistic.MIN))
        );
    }

    private static void assertFinite(double value, Statistic s) {
        Assertions.assertTrue(Double.isFinite(value), () -> s.toString() + " isFinite");
    }

    @ParameterizedTest
    @MethodSource
    void testBiased(Statistic stat, int[] values, boolean[] options, double[] results) {
        final IntStatistics statistics1 = IntStatistics.builder(stat).build(values);

        StatisticsConfiguration c = StatisticsConfiguration.withDefaults();
        DoubleSupplier s = null;
        // Note the circular loop to check setting back to the start option
        for (int index = 0; index <= options.length; index++) {
            final int i = index % options.length;
            final boolean value = options[i];
            c = c.withBiased(value);
            Assertions.assertSame(statistics1, statistics1.setConfiguration(c));

            Assertions.assertEquals(results[i], statistics1.getAsDouble(stat),
                () -> options[i] + " get: " + BaseIntStatisticTest.format(values));
            final DoubleSupplier s1 = statistics1.getResult(stat);
            Assertions.assertEquals(results[i], s1.getAsDouble(),
                () -> options[i] + " supplier: " + BaseIntStatisticTest.format(values));

            // Config change does not propagate to previous supplier
            if (s != null) {
                final int j = (i - 1 + options.length) % options.length;
                Assertions.assertEquals(results[j], s.getAsDouble(),
                    () -> options[j] + " previous supplier: " + BaseIntStatisticTest.format(values));
            }
            s = s1;

            // Set through the builder
            final IntStatistics statistics2 = IntStatistics.builder(stat)
                .setConfiguration(c).build(values);
            Assertions.assertEquals(results[i], statistics2.getAsDouble(stat),
                () -> options[i] + " get via builder: " + BaseIntStatisticTest.format(values));
        }
    }

    static Stream<Arguments> testBiased() {
        final Stream.Builder<Arguments> builder = Stream.builder();
        // Data must generate different results for different options
        final int[][] data = {
            {1},
            {1, 2},
            {1, 2, 4},
            {1, 2, 4, 8},
            {1, 3, 6, 7, 19},
        };
        final Statistic[] stats = {Statistic.STANDARD_DEVIATION, Statistic.VARIANCE,
            Statistic.SKEWNESS, Statistic.KURTOSIS};
        final int[] diff = new int[stats.length];
        for (final int[] d : data) {
            diff[0] += addBooleanOptionCase(builder, d, stats[0], IntStandardDeviation::of, IntStandardDeviation::setBiased);
            diff[1] += addBooleanOptionCase(builder, d, stats[1], IntVariance::of, IntVariance::setBiased);
            diff[2] += addBooleanOptionCase(builder, d, stats[2], Skewness::of, Skewness::setBiased);
            diff[3] += addBooleanOptionCase(builder, d, stats[3], Kurtosis::of, Kurtosis::setBiased);
        }
        // Check the option generated some different results for each statistic
        for (int i = 0; i < stats.length; i++) {
            final Statistic s = stats[i];
            Assertions.assertTrue(diff[i] > data.length, () -> "Option never changes the result: " + s);
        }
        return builder.build();
    }

    /**
     * Adds the expected results for the boolean option of the specified {@code statistic}
     * computed using the provided {@code values}.
     *
     * <p>This method returns the number of unique results. It does not enforce that the
     * results are different.
     *
     * @param <T> {@link StatisticResult} being computed.
     * @param builder Argument builder.
     * @param values Values.
     * @param statistic Statistic.
     * @param arrayConstructor Constructor using an array of values.
     * @param setter Option setter.
     * @return the number of unique results
     */
    private static <T extends StatisticResult & StatisticAccumulator<T>> int addBooleanOptionCase(
            Stream.Builder<Arguments> builder, int[] values, Statistic statistic,
            Function<int[], T> arrayConstructor, BiConsumer<T, Boolean> setter) {
        final T stat = arrayConstructor.apply(values);
        final boolean[] options = {true, false};
        final double[] results = new double[2];
        final Set<Double> all = new HashSet<>();
        for (int i = 0; i < options.length; i++) {
            setter.accept(stat, options[i]);
            results[i] = stat.getAsDouble();
            all.add(results[i]);
        }
        builder.accept(Arguments.of(statistic, values, options, results));
        return all.size();
    }

    @Test
    void testMoments() {
        // Hits coverage within the builder for when kurtosis is ordered before skewness
        final IntStatistics s = IntStatistics.of(Statistic.KURTOSIS, Statistic.SKEWNESS);
        Assertions.assertTrue(s.isSupported(Statistic.KURTOSIS));
        Assertions.assertTrue(s.isSupported(Statistic.SKEWNESS));
    }
}
