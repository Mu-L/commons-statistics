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

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Arrays;
import java.util.function.Supplier;
import org.apache.commons.rng.UniformRandomProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test for {@link Kurtosis}.
 */
final class KurtosisTest {
    private static final int ULP_ARRAY = 30;
    private static final int ULP_STREAM = 30;
    private static final int ULP_COMBINE_ACCEPT = 70;
    private static final int ULP_COMBINE_OF = 30;

    @Test
    void testEmpty() {
        final Kurtosis kurt = Kurtosis.create();
        for (int i = 0; i < 4; i++) {
            Assertions.assertEquals(Double.NaN, kurt.getAsDouble());
            kurt.accept(i);
        }
        Assertions.assertNotEquals(Double.NaN, kurt.getAsDouble());
    }

    @Test
    void testNaN() {
        Kurtosis kurtosis = Kurtosis.create();
        double[] testArray = {Double.NaN, +0.0d, -0.0d, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (double value : testArray) {
            kurtosis.accept(value);
        }
        Assertions.assertEquals(Double.NaN, kurtosis.getAsDouble());
    }

    @ParameterizedTest
    @MethodSource(value = "org.apache.commons.statistics.descriptive.TestData#testValues")
    void testKurtosis(double[] values) {
        final double expected = computeExpectedKurtosis(values);
        Kurtosis kurt = Kurtosis.create();
        for (double value : values) {
            kurt.accept(value);
        }
        assertStreaming(values.length, expected, kurt.getAsDouble(), ULP_STREAM, () -> "kurtosis");
        TestHelper.assertEquals(expected, Kurtosis.of(values).getAsDouble(), ULP_ARRAY, () -> "of (values)");

        Kurtosis kurt2 = Kurtosis.of();
        for (double value : values) {
            kurt2.accept(value);
        }
        Assertions.assertEquals(kurt.getAsDouble(), kurt2.getAsDouble(), "of() + values");
    }

    @ParameterizedTest
    @MethodSource(value = "org.apache.commons.statistics.descriptive.TestData#testValues")
    void testParallelStream(double[] values) {
        final double expected = computeExpectedKurtosis(values);
        final double actual = Arrays.stream(values)
                .parallel()
                .collect(Kurtosis::create, Kurtosis::accept, Kurtosis::combine)
                .getAsDouble();
        assertStreaming(values.length, expected, actual, ULP_COMBINE_ACCEPT, () -> "parallel stream");
    }

    @ParameterizedTest
    @MethodSource(value = "org.apache.commons.statistics.descriptive.TestData#testValues")
    void testKurtosisRandomOrder(double[] values) {
        UniformRandomProvider rng = TestHelper.createRNG();
        for (int i = 1; i <= 10; i++) {
            testKurtosis(TestHelper.shuffle(rng, values));
            testParallelStream(TestHelper.shuffle(rng, values));
        }
    }

    @ParameterizedTest
    @MethodSource(value = "org.apache.commons.statistics.descriptive.TestData#testValuesNonFinite")
    void testKurtosisNonFinite(double[] values) {
        final double expected = Double.NaN;
        Kurtosis kurt = Kurtosis.create();
        for (double value : values) {
            kurt.accept(value);
        }
        Assertions.assertEquals(expected, kurt.getAsDouble(), "kurtosis non-finite");
        Assertions.assertEquals(expected, Kurtosis.of(values).getAsDouble(), "of (values) non-finite");

        Kurtosis kurt2 = Kurtosis.of();
        for (double value : values) {
            kurt2.accept(value);
        }
        Assertions.assertEquals(kurt.getAsDouble(), kurt2.getAsDouble(), "of() + values non-finite");
    }

    @ParameterizedTest
    @MethodSource(value = "org.apache.commons.statistics.descriptive.TestData#testValuesNonFinite")
    void testParallelStreamNonFinite(double[] values) {
        final double expected = Double.NaN;
        final double ans = Arrays.stream(values)
                .parallel()
                .collect(Kurtosis::create, Kurtosis::accept, Kurtosis::combine)
                .getAsDouble();
        Assertions.assertEquals(expected, ans, "parallel stream non-finite");
    }

    @ParameterizedTest
    @MethodSource(value = "org.apache.commons.statistics.descriptive.TestData#testValuesNonFinite")
    void testKurtosisRandomOrderNonFinite(double[] values) {
        UniformRandomProvider rng = TestHelper.createRNG();
        for (int i = 1; i <= 10; i++) {
            testKurtosisNonFinite(TestHelper.shuffle(rng, values));
            testParallelStreamNonFinite(TestHelper.shuffle(rng, values));
        }
    }

    @ParameterizedTest
    @MethodSource(value = "org.apache.commons.statistics.descriptive.TestData#testCombine")
    void testCombine(double[] array1, double[] array2) {
        final double[] combinedArray = TestHelper.concatenate(array1, array2);
        final double expected = computeExpectedKurtosis(combinedArray);
        Kurtosis kurt1 = Kurtosis.create();
        Kurtosis kurt2 = Kurtosis.create();
        Arrays.stream(array1).forEach(kurt1);
        Arrays.stream(array2).forEach(kurt2);
        final double kurt1BeforeCombine = kurt1.getAsDouble();
        final double kurt2BeforeCombine = kurt2.getAsDouble();
        kurt1.combine(kurt2);
        TestHelper.assertEquals(expected, kurt1.getAsDouble(), ULP_COMBINE_ACCEPT, () -> "combine");
        Assertions.assertEquals(kurt2BeforeCombine, kurt2.getAsDouble());
        // Combine in reverse order
        Kurtosis kurt1b = Kurtosis.create();
        Arrays.stream(array1).forEach(kurt1b);
        kurt2.combine(kurt1b);
        Assertions.assertEquals(kurt1.getAsDouble(), kurt2.getAsDouble(), () -> "combine reversed");
        Assertions.assertEquals(kurt1BeforeCombine, kurt1b.getAsDouble());
    }

    @ParameterizedTest
    @MethodSource(value = "org.apache.commons.statistics.descriptive.TestData#testCombine")
    void testCombineRandomOrder(double[] array1, double[] array2) {
        UniformRandomProvider rng = TestHelper.createRNG();
        double[] data = TestHelper.concatenate(array1, array2);
        final int n = array1.length;
        for (int i = 1; i <= 10; i++) {
            for (int j = 1; j <= 10; j++) {
                TestHelper.shuffle(rng, array1);
                TestHelper.shuffle(rng, array2);
                testCombine(array1, array2);
            }
            TestHelper.shuffle(rng, data);
            System.arraycopy(data, 0, array1, 0, n);
            System.arraycopy(data, n, array2, 0, array2.length);
            testCombine(array1, array2);
        }
    }

    @ParameterizedTest
    @MethodSource(value = "org.apache.commons.statistics.descriptive.TestData#testCombine")
    void testArrayOfArrays(double[] array1, double[] array2) {
        final double[] combinedArray = TestHelper.concatenate(array1, array2);
        final double expected = computeExpectedKurtosis(combinedArray);
        final double[][] values = {array1, array2};
        final double actual = Arrays.stream(values)
                .map(Kurtosis::of)
                .reduce(Kurtosis::combine)
                .map(Kurtosis::getAsDouble)
                .orElseThrow(RuntimeException::new);
        TestHelper.assertEquals(expected, actual, ULP_COMBINE_OF, () -> "array of arrays combined kurtosis");
    }

    @ParameterizedTest
    @MethodSource(value = "org.apache.commons.statistics.descriptive.TestData#testCombineNonFinite")
    void testCombineNonFinite(double[][] values) {
        final double expected = Double.NaN;
        Kurtosis kurt1 = Kurtosis.create();
        Kurtosis kurt2 = Kurtosis.create();
        Arrays.stream(values[0]).forEach(kurt1);
        Arrays.stream(values[1]).forEach(kurt2);
        final double mean2BeforeCombine = kurt2.getAsDouble();
        kurt1.combine(kurt2);
        Assertions.assertEquals(expected, kurt1.getAsDouble(), "combine non-finite");
        Assertions.assertEquals(mean2BeforeCombine, kurt2.getAsDouble());
    }

    @ParameterizedTest
    @MethodSource(value = "org.apache.commons.statistics.descriptive.TestData#testCombineNonFinite")
    void testCombineRandomOrderNonFinite(double[][] values) {
        UniformRandomProvider rng = TestHelper.createRNG();
        final double[] data = TestHelper.concatenate(values[0], values[1]);
        final int n = values[0].length;
        for (int i = 1; i <= 10; i++) {
            for (int j = 1; j <= 10; j++) {
                TestHelper.shuffle(rng, values[0]);
                TestHelper.shuffle(rng, values[1]);
                testCombineNonFinite(values);
            }
            TestHelper.shuffle(rng, data);
            System.arraycopy(data, 0, values[0], 0, n);
            System.arraycopy(data, n, values[1], 0, values[1].length);
            testCombineNonFinite(values);
        }
    }

    @ParameterizedTest
    @MethodSource(value = "org.apache.commons.statistics.descriptive.TestData#testCombineNonFinite")
    void testArrayOfArraysNonFinite(double[][] values) {
        final double expected = Double.NaN;
        final double actual = Arrays.stream(values)
                .map(Kurtosis::of)
                .reduce(Kurtosis::combine)
                .map(Kurtosis::getAsDouble)
                .orElseThrow(RuntimeException::new);
        Assertions.assertEquals(expected, actual, "array of arrays combined kurtosis non-finite");
    }

    @ParameterizedTest
    @MethodSource(value = "org.apache.commons.statistics.descriptive.TestData#testMultiCombine")
    void testMultiCombine(double[][] values) {
        final double[] combinedArray = TestHelper.concatenate(values);
        final double expected = computeExpectedKurtosis(combinedArray);
        final double actual = Arrays.stream(values)
                .map(Kurtosis::of)
                .reduce(Kurtosis::combine)
                .map(Kurtosis::getAsDouble)
                .orElseThrow(RuntimeException::new);
        // Level to pass all test data
        final int ulp = 560;
        TestHelper.assertEquals(expected, actual, ulp, () -> "multi combine");
    }

    @Test
    void testNonFiniteSumOfCubedDeviations() {
        // The value 2^500 will overflow the sum of cubed deviations but not
        // the sum of squared deviations
        final Kurtosis kurt = Kurtosis.of(0, 0, 0, 0x1.0p500);
        Assertions.assertEquals(Double.NaN, kurt.getAsDouble());
    }

    @Test
    void testNonFiniteSumOfFourthDeviations() {
        // The value 2^300 will overflow the sum of fourth deviations
        // but not the sum of cubed deviations
        final Kurtosis kurt = Kurtosis.of(0, 0, 0, 0x1.0p300);
        Assertions.assertEquals(Double.NaN, kurt.getAsDouble());
    }

    /**
     * Compute expected kurtosis.
     * This method returns NaN when the intermediates do not have a finite double value.
     *
     * @param values Values.
     * @return kurtosis
     */
    private static double computeExpectedKurtosis(double[] values) {
        final long n = values.length;
        if (n < 4) {
            return Double.NaN;
        }
        // High precision deviations.
        // Capture the mean for reuse.
        final BigDecimal[] mean = new BigDecimal[1];
        final BigDecimal s2 = TestHelper.computeExpectedSumOfSquaredDeviations(values, mean);
        // Check for a zero denominator.
        if (s2.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }
        if (!Double.isFinite(s2.doubleValue())) {
            return Double.NaN;
        }
        final BigDecimal s4 = SumOfFourthDeviationsTest.computeExpectedSumOfFourthDeviations(values, mean[0]);
        if (!Double.isFinite(s2.doubleValue())) {
            return Double.NaN;
        }
        // Compute in BigDecimal so the order of operations does not change
        // the double precision result.
        final BigDecimal np1 = BigDecimal.valueOf(n + 1);
        final BigDecimal n0 = BigDecimal.valueOf(n);
        final BigDecimal nm1 = BigDecimal.valueOf(n - 1);
        final BigDecimal nm2 = BigDecimal.valueOf(n - 2);
        final BigDecimal nm3 = BigDecimal.valueOf(n - 3);
        final BigDecimal a = np1.multiply(n0).multiply(nm1).multiply(s4);
        final BigDecimal b = nm2.multiply(nm3).multiply(s2.pow(2));
        final BigDecimal c = BigDecimal.valueOf(3).multiply(nm1.pow(2));
        final BigDecimal d = nm2.multiply(nm3);
        return a.divide(b, MathContext.DECIMAL128).subtract(
            c.divide(d, MathContext.DECIMAL128)).doubleValue();
    }

    /**
     * Assert the actual result from the streaming algorithm.
     *
     * <p>Note: Close to zero the streaming method is very dependent on the input order
     * and can be very far from the expected result in ULPs.
     * This simply checks the value is small.
     *
     * @param size Size of the data.
     * @param expected Expected value.
     * @param actual Actual value.
     * @param ulp ULP tolerance.
     * @param msg Failure message.
     */
    private static void assertStreaming(int size, double expected, double actual, int ulp,
        Supplier<String> msg) {
        if (size > 2 && Math.abs(expected) < 1e-10) {
            // Close to zero the streaming method is very dependent on the input order.
            // This simply checks the value is small.
            Assertions.assertEquals(expected, actual, 4e-13, msg);
        } else {
            TestHelper.assertEquals(expected, actual, ULP_COMBINE_ACCEPT, msg);
        }
    }
}