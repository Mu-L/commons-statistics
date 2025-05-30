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
 * Represents a function that accepts two objects and a range and produces a result.
 *
 * <p>This interface is similar to {@link java.util.function.BiFunction} with the addition
 * of a range {@code [from, to)} argument to the functional method.
 *
 * @param <T> the type of the first argument to the function
 * @param <U> the type of the second argument to the function
 * @param <R> the type of the result of the function
 */
@FunctionalInterface
interface RangeBiFunction<T, U, R> {
    /**
     * Applies this function to the given arguments.
     *
     * @param t the function argument
     * @param u the second function argument
     * @param from Inclusive start of the range.
     * @param to Exclusive end of the range.
     * @return the function result
     */
    R apply(T t, U u, int from, int to);
}
