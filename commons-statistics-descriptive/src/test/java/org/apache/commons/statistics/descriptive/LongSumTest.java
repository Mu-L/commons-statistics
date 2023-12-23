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

import java.math.BigInteger;
import java.util.Arrays;
import java.util.stream.Stream;
import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.statistics.distribution.DoubleTolerance;
import org.apache.commons.statistics.distribution.DoubleTolerances;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Test for {@link LongSum}.
 */
final class LongSumTest extends BaseLongStatisticTest<LongSum> {

    @Override
    protected ResultType getResultType() {
        return ResultType.BIG_INTEGER;
    }

    @Override
    protected LongSum create() {
        return LongSum.create();
    }

    @Override
    protected LongSum create(long... values) {
        return LongSum.of(values);
    }

    @Override
    protected DoubleStatistic createAsDoubleStatistic(long... values) {
        return Sum.of(Arrays.stream(values).asDoubleStream().toArray());
    }

    @Override
    protected DoubleTolerance getToleranceAsDouble() {
        // Floating-point sum may be inexact.
        // Currently the double sum matches on the standard test data.
        // It fails on large random data added in streamTestData().
        return DoubleTolerances.ulps(5);
    }

    @Override
    protected StatisticResult getEmptyValue() {
        // It does not matter that this returns a IntStatisticResult
        // rather than a BigIntegerStatisticResult
        return createStatisticResult(0);
    }

    @Override
    protected StatisticResult getExpectedValue(long[] values) {
        final BigInteger x = Arrays.stream(values)
            .mapToObj(BigInteger::valueOf)
            .reduce(BigInteger.ZERO, BigInteger::add);
        return createStatisticResult(x);
    }

    @Override
    protected Stream<StatisticTestData> streamTestData() {
        // A null seed will create a different RNG each time
        final UniformRandomProvider rng = TestHelper.createRNG(null);
        return Stream.of(
            addCase(Long.MAX_VALUE, 1, 2, 3, 4, -20, Long.MAX_VALUE),
            addCase(Long.MIN_VALUE, -1, -2, -3, -4, 20, Long.MIN_VALUE),
            addCase(rng.longs(5).toArray()),
            addCase(rng.longs(10).toArray()),
            addCase(rng.longs(20).toArray()),
            addCase(rng.longs(40).toArray())
        );
    }

    /**
     * Test large integer sums that overflow a {@code long}.
     * Overflow is created by repeat addition.
     *
     * <p>Note: Currently no check is made for overflow in the
     * count of observations. If this overflows then the statistic
     * will be incorrect so the test is limited to {@code n < 2^63}.
     */
    @ParameterizedTest
    @CsvSource({
        "-1628367672438123811, -97927322516725738, 60",
        "3279208082627834682, 4234564566706285432, 61",
        "9223372036854775807, 9223372036854775806, 61",
        "-9223372036854775808, -9223372036854775807, 61",
    })
    void testLongOverflow(long x, long y, int exp) {
        final LongSum s = LongSum.of(x, y);
        BigInteger sum = BigInteger.valueOf(x).add(BigInteger.valueOf(y));
        for (int i = 0; i < exp; i++) {
            // Assumes the sum as a long will overflow
            s.combine(s);
            sum = sum.shiftLeft(1);
            Assertions.assertEquals(sum, s.getAsBigInteger());
        }
    }
}
