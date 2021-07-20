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
package org.apache.commons.statistics.distribution;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test cases for FDistribution.
 * Extends ContinuousDistributionAbstractTest.  See class javadoc for
 * ContinuousDistributionAbstractTest for details.
 */
class FDistributionTest extends ContinuousDistributionAbstractTest {

    //---------------------- Override tolerance --------------------------------

    @BeforeEach
    void customSetUp() {
        setTolerance(1e-9);
    }

    //-------------- Implementations for abstract methods ----------------------

    /** Creates the default continuous distribution instance to use in tests. */
    @Override
    public FDistribution makeDistribution() {
        return new FDistribution(5.0, 6.0);
    }

    /** Creates the default cumulative probability distribution test input values. */
    @Override
    public double[] makeCumulativeTestPoints() {
        // quantiles computed using R version 2.9.2
        return new double[] {0.0346808448626, 0.0937009113303, 0.143313661184, 0.202008445998, 0.293728320107,
                             20.8026639595, 8.74589525602, 5.98756512605, 4.38737418741, 3.10751166664};
    }

    /** Creates the default cumulative probability density test expected values. */
    @Override
    public double[] makeCumulativeTestValues() {
        return new double[] {0.001, 0.01, 0.025, 0.05, 0.1, 0.999, 0.990, 0.975, 0.950, 0.900};
    }

    /** Creates the default probability density test expected values. */
    @Override
    public double[] makeDensityTestValues() {
        return new double[] {0.0689156576706, 0.236735653193, 0.364074131941, 0.481570789649, 0.595880479994,
                             0.000133443915657, 0.00286681303403, 0.00969192007502, 0.0242883861471, 0.0605491314658};
    }

    @Override
    public double[] makeCumulativePrecisionTestPoints() {
        return new double[] {1e-7, 4e-8, 9e-8};
    }

    @Override
    public double[] makeCumulativePrecisionTestValues() {
        // These were created using WolframAlpha
        return new double[] {1.578691625481747e-17, 1.597523916857153e-18, 1.2131195257872846e-17};
    }

    @Override
    public double[] makeSurvivalPrecisionTestPoints() {
        return new double[] {1e6, 42e5, 63e5};
    }

    @Override
    public double[] makeSurvivalPrecisionTestValues() {
        // These were created using WolframAlpha
        return new double[] {1.1339943867175144e-17, 1.5306104409634358e-19, 4.535143828961954e-20};
    }

    //-------------------- Additional test cases -------------------------------

    @Test
    void testCumulativeProbabilityExtremes() {
        setCumulativeTestPoints(new double[] {-2, 0});
        setCumulativeTestValues(new double[] {0, 0});
        verifyCumulativeProbabilities();
    }

    @Test
    void testInverseCumulativeProbabilityExtremes() {
        setInverseCumulativeTestPoints(new double[] {0, 1});
        setInverseCumulativeTestValues(new double[] {0, Double.POSITIVE_INFINITY});
        verifyInverseCumulativeProbabilities();
    }

    @Test
    void testParameterAccessors() {
        final FDistribution dist = makeDistribution();
        Assertions.assertEquals(5d, dist.getNumeratorDegreesOfFreedom());
        Assertions.assertEquals(6d, dist.getDenominatorDegreesOfFreedom());
    }

    @Test
    void testConstructorPrecondition1() {
        Assertions.assertThrows(DistributionException.class, () -> new FDistribution(0, 1));
    }
    @Test
    void testConstructorPrecondition2() {
        Assertions.assertThrows(DistributionException.class, () -> new FDistribution(1, 0));
    }

    @Test
    void testMoments() {
        final double tol = 1e-9;
        FDistribution dist;

        dist = new FDistribution(1, 2);
        Assertions.assertTrue(Double.isNaN(dist.getMean()));
        Assertions.assertTrue(Double.isNaN(dist.getVariance()));

        dist = new FDistribution(1, 3);
        Assertions.assertEquals(3d / (3d - 2d), dist.getMean(), tol);
        Assertions.assertTrue(Double.isNaN(dist.getVariance()));

        dist = new FDistribution(1, 5);
        Assertions.assertEquals(5d / (5d - 2d), dist.getMean(), tol);
        Assertions.assertEquals((2d * 5d * 5d * 4d) / 9d, dist.getVariance(), tol);
    }

    @Test
    void testLargeDegreesOfFreedom() {
        final FDistribution fd = new FDistribution(100000, 100000);
        final double p = fd.cumulativeProbability(.999);
        final double x = fd.inverseCumulativeProbability(p);
        Assertions.assertEquals(.999, x, 1.0e-5);
    }

    @Test
    void testSmallDegreesOfFreedom() {
        FDistribution fd = new FDistribution(1, 1);
        double p = fd.cumulativeProbability(0.975);
        double x = fd.inverseCumulativeProbability(p);
        Assertions.assertEquals(0.975, x, 1.0e-5);

        fd = new FDistribution(1, 2);
        p = fd.cumulativeProbability(0.975);
        x = fd.inverseCumulativeProbability(p);
        Assertions.assertEquals(0.975, x, 1.0e-5);
    }

    @Test
    void testMath785() {
        // this test was failing due to inaccurate results from ContinuedFraction.
        final double prob = 0.01;
        final FDistribution f = new FDistribution(200000, 200000);
        final double result = f.inverseCumulativeProbability(prob);
        Assertions.assertTrue(result < 1.0, "Failing to calculate inverse cumulative probability");
    }
}
