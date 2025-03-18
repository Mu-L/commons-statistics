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

/**
 * Computes the standard deviation of the available values. The default implementations uses
 * the following definition of the <em>sample standard deviation</em>:
 *
 * <p>\[ \sqrt{ \tfrac{1}{n-1} \sum_{i=1}^n (x_i-\overline{x})^2 } \]
 *
 * <p>where \( \overline{x} \) is the sample mean, and \( n \) is the number of samples.
 *
 * <ul>
 *   <li>The result is {@code NaN} if no values are added.
 *   <li>The result is {@code NaN} if any of the values is {@code NaN} or infinite.
 *   <li>The result is {@code NaN} if the sum of the squared deviations from the mean is infinite.
 *   <li>The result is zero if there is one finite value in the data set.
 * </ul>
 *
 * <p>The use of the term \( n − 1 \) is called Bessel's correction. Omitting the square root,
 * this provides an unbiased estimator of the variance of a hypothetical infinite population. If the
 * {@link #setBiased(boolean) biased} option is enabled the normalisation factor is
 * changed to \( \frac{1}{n} \) for a biased estimator of the <em>sample variance</em>.
 * Note however that square root is a concave function and thus introduces negative bias
 * (by Jensen's inequality), which depends on the distribution, and thus the corrected sample
 * standard deviation (using Bessel's correction) is less biased, but still biased.
 *
 * <p>The {@link #accept(double)} method uses a recursive updating algorithm based on West's
 * algorithm (see Chan and Lewis (1979)).
 *
 * <p>The {@link #of(double...)} method uses the corrected two-pass algorithm from
 * Chan <i>et al</i>, (1983).
 *
 * <p>Note that adding values using {@link #accept(double) accept} and then executing
 * {@link #getAsDouble() getAsDouble} will
 * sometimes give a different, less accurate, result than executing
 * {@link #of(double...) of} with the full array of values. The former approach
 * should only be used when the full array of values is not available.
 *
 * <p>Supports up to 2<sup>63</sup> (exclusive) observations.
 * This implementation does not check for overflow of the count.
 *
 * <p>This class is designed to work with (though does not require)
 * {@linkplain java.util.stream streams}.
 *
 * <p><strong>Note that this instance is not synchronized.</strong> If
 * multiple threads access an instance of this class concurrently, and at least
 * one of the threads invokes the {@link java.util.function.DoubleConsumer#accept(double) accept} or
 * {@link StatisticAccumulator#combine(StatisticResult) combine} method, it must be synchronized externally.
 *
 * <p>However, it is safe to use {@link java.util.function.DoubleConsumer#accept(double) accept}
 * and {@link StatisticAccumulator#combine(StatisticResult) combine}
 * as {@code accumulator} and {@code combiner} functions of
 * {@link java.util.stream.Collector Collector} on a parallel stream,
 * because the parallel instance of {@link java.util.stream.Stream#collect Stream.collect()}
 * provides the necessary partitioning, isolation, and merging of results for
 * safe and efficient parallel execution.
 *
 * <p>References:
 * <ul>
 *   <li>Chan and Lewis (1979)
 *       Computing standard deviations: accuracy.
 *       Communications of the ACM, 22, 526-531.
 *       <a href="http://doi.acm.org/10.1145/359146.359152">doi: 10.1145/359146.359152</a>
 *   <li>Chan, Golub and Levesque (1983)
 *       Algorithms for Computing the Sample Variance: Analysis and Recommendations.
 *       American Statistician, 37, 242-247.
 *       <a href="https://doi.org/10.2307/2683386">doi: 10.2307/2683386</a>
 * </ul>
 *
 * @see <a href="https://en.wikipedia.org/wiki/Standard_deviation">Standard deviation (Wikipedia)</a>
 * @see <a href="https://en.wikipedia.org/wiki/Bessel%27s_correction">Bessel&#39;s correction</a>
 * @see <a href="https://en.wikipedia.org/wiki/Jensen%27s_inequality">Jensen&#39;s inequality</a>
 * @see Variance
 * @since 1.1
 */
public final class StandardDeviation implements DoubleStatistic, StatisticAccumulator<StandardDeviation> {

    /**
     * An instance of {@link SumOfSquaredDeviations}, which is used to
     * compute the standard deviation.
     */
    private final SumOfSquaredDeviations ss;

    /** Flag to control if the statistic is biased, or should use a bias correction. */
    private boolean biased;

    /**
     * Create an instance.
     */
    private StandardDeviation() {
        this(new SumOfSquaredDeviations());
    }

    /**
     * Creates an instance with the sum of squared deviations from the mean.
     *
     * @param ss Sum of squared deviations.
     */
    StandardDeviation(SumOfSquaredDeviations ss) {
        this.ss = ss;
    }

    /**
     * Creates an instance.
     *
     * <p>The initial result is {@code NaN}.
     *
     * @return {@code StandardDeviation} instance.
     */
    public static StandardDeviation create() {
        return new StandardDeviation();
    }

    /**
     * Returns an instance populated using the input {@code values}.
     *
     * <p>Note: {@code StandardDeviation} computed using {@link #accept(double) accept} may be
     * different from this standard deviation.
     *
     * <p>See {@link StandardDeviation} for details on the computing algorithm.
     *
     * @param values Values.
     * @return {@code StandardDeviation} instance.
     */
    public static StandardDeviation of(double... values) {
        return new StandardDeviation(SumOfSquaredDeviations.of(values));
    }

    /**
     * Returns an instance populated using the specified range of {@code values}.
     *
     * <p>Note: {@code StandardDeviation} computed using {@link #accept(double) accept} may be
     * different from this standard deviation.
     *
     * <p>See {@link StandardDeviation} for details on the computing algorithm.
     *
     * @param values Values.
     * @param from Inclusive start of the range.
     * @param to Exclusive end of the range.
     * @return {@code StandardDeviation} instance.
     * @throws IndexOutOfBoundsException if the sub-range is out of bounds
     */
    public static StandardDeviation ofRange(double[] values, int from, int to) {
        Statistics.checkFromToIndex(from, to, values.length);
        return new StandardDeviation(SumOfSquaredDeviations.ofRange(values, from, to));
    }

    /**
     * Updates the state of the statistic to reflect the addition of {@code value}.
     *
     * @param value Value.
     */
    @Override
    public void accept(double value) {
        ss.accept(value);
    }

    /**
     * Gets the standard deviation of all input values.
     *
     * <p>When no values have been added, the result is {@code NaN}.
     *
     * @return standard deviation of all values.
     */
    @Override
    public double getAsDouble() {
        // This method checks the sum of squared is finite
        // to provide a consistent NaN when the computation is not possible.
        // Note: The SS checks for n=0 and returns NaN.
        final double m2 = ss.getSumOfSquaredDeviations();
        if (!Double.isFinite(m2)) {
            return Double.NaN;
        }
        final long n = ss.n;
        // Avoid a divide by zero
        if (n == 1) {
            return 0;
        }
        return biased ? Math.sqrt(m2 / n) : Math.sqrt(m2 / (n - 1));
    }

    @Override
    public StandardDeviation combine(StandardDeviation other) {
        ss.combine(other.ss);
        return this;
    }

    /**
     * Sets the value of the biased flag. The default value is {@code false}. The bias
     * term refers to the computation of the variance; the standard deviation is returned
     * as the square root of the biased or unbiased <em>sample variance</em>. For further
     * details see {@link Variance#setBiased(boolean) Variance.setBiased}.
     *
     * <p>This flag only controls the final computation of the statistic. The value of
     * this flag will not affect compatibility between instances during a
     * {@link #combine(StandardDeviation) combine} operation.
     *
     * @param v Value.
     * @return {@code this} instance
     * @see Variance#setBiased(boolean)
     */
    public StandardDeviation setBiased(boolean v) {
        biased = v;
        return this;
    }
}
