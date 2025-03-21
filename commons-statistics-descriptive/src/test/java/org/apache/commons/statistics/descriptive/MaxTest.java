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

import java.util.Arrays;
import java.util.stream.Stream;
import org.apache.commons.statistics.distribution.DoubleTolerance;
import org.apache.commons.statistics.distribution.DoubleTolerances;

/**
 * Test for {@link Max}.
 */
final class MaxTest extends BaseDoubleStatisticTest<Max> {

    @Override
    protected Max create() {
        return Max.create();
    }

    @Override
    protected Max create(double... values) {
        return Max.of(values);
    }

    @Override
    protected Max create(double[] values, int from, int to) {
        return Max.ofRange(values, from, to);
    }

    @Override
    protected double getEmptyValue() {
        return Double.NEGATIVE_INFINITY;
    }

    @Override
    protected double getExpectedValue(double[] values) {
        // Use the JDK as a reference implementation
        return Arrays.stream(values).max().orElse(getEmptyValue());
    }

    @Override
    protected double getExpectedNonFiniteValue(double[] values) {
        return getExpectedValue(values);
    }

    @Override
    protected DoubleTolerance getTolerance() {
        return DoubleTolerances.equals();
    }

    @Override
    protected Stream<StatisticTestData> streamTestData() {
        return Stream.of(
            // Differentiate +/- zero
            addCase(-0.0, 0.0),
            addCase(-3, -2, -1, -0.0, 0.0),
            addCase(-1, -0.0, 0.0, 1),
            // Differentiate -MAX_VALUE from the default of -infinity
            addCase(-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE),
            addCase(1, 2, 3, 4, Double.MAX_VALUE)
        );
    }
}
