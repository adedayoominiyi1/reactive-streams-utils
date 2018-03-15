/************************************************************************
 * Licensed under Public Domain (CC0)                                    *
 *                                                                       *
 * To the extent possible under law, the person who associated CC0 with  *
 * this code has waived all copyright and related or neighboring         *
 * rights to this code.                                                  *
 *                                                                       *
 * You should have received a copy of the CC0 legalcode along with this  *
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.*
 ************************************************************************/

package org.reactivestreams.utils.spi;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;

/**
 * A stage of a Reactive Streams graph.
 * <p>
 * A Reactive Streams engine will walk a graph of stages to produce {@link Publisher}, {@link Subscriber} and
 * {@link Processor} instances that handle the stream according to the sequence of stages.
 */
public interface Stage {

  /**
   * Whether this stage has an inlet - ie, when built, will it implement the {@link Subscriber} interface?
   *
   * @return True if this stage has an inlet.
   */
  default boolean hasInlet() {
    return false;
  }

  /**
   * Whether this stage has an outlet - ie, when built, will it implement the {@link Publisher} interface?
   *
   * @return True if this stage has an outlet.
   */
  default boolean hasOutlet() {
    return false;
  }

  /**
   * Convenience interface for inlet stages.
   */
  interface Inlet extends Stage {
    @Override
    default boolean hasInlet() {
      return true;
    }
  }

  /**
   * Convenience interface for outlet stages.
   */
  interface Outlet extends Stage {
    @Override
    default boolean hasOutlet() {
      return true;
    }
  }

  /**
   * A map stage.
   * <p>
   * The given mapper function should be invoked on each element consumed, and the output of the function should be
   * emitted.
   * <p>
   * Any {@link RuntimeException} thrown by the function should be propagated down the stream as an error.
   */
  final class Map implements Inlet, Outlet {
    private final Function<?, ?> mapper;

    public Map(Function<?, ?> mapper) {
      this.mapper = mapper;
    }

    /**
     * The mapper function.
     *
     * @return The mapper function.
     */
    public Function<?, ?> getMapper() {
      return mapper;
    }
  }

  /**
   * A filter stage.
   * <p>
   * The given predicate should be supplied when the stream is first run, and then invoked on each element consumed.
   * If it returns true, the element should be emitted.
   * <p>
   * Any {@link RuntimeException} thrown by the predicate should be propagated down the stream as an error.
   */
  final class Filter implements Inlet, Outlet {
    private final Supplier<Predicate<?>> predicate;

    public Filter(Supplier<Predicate<?>> predicate) {
      this.predicate = predicate;
    }

    /**
     * The predicate.
     *
     * @return The predicate.
     */
    public Supplier<Predicate<?>> getPredicate() {
      return predicate;
    }
  }

  /**
   * A take while stage.
   * <p>
   * The given predicate should be supplied when the stream is first run, and then invoked on each element consumed.
   * When it returns true, the element should be emitted, when it returns false, the element should only be emitted if
   * inclusive is true, and then the stream should be completed.
   * <p>
   * Any {@link RuntimeException} thrown by the predicate should be propagated down the stream as an error.
   */
  final class TakeWhile implements Inlet, Outlet {
    private final Supplier<Predicate<?>> predicate;
    private final boolean inclusive;

    public TakeWhile(Supplier<Predicate<?>> predicate, boolean inclusive) {
      this.predicate = predicate;
      this.inclusive = inclusive;
    }

    /**
     * The predicate.
     *
     * @return The predicate.
     */
    public Supplier<Predicate<?>> getPredicate() {
      return predicate;
    }

    /**
     * Whether the element that this returns false on should be emitted or not.
     */
    public boolean isInclusive() {
      return inclusive;
    }
  }

  /**
   * A publisher stage.
   * <p>
   * The given publisher should be subscribed to whatever subscriber is provided to this graph, via any other subsequent
   * stages.
   */
  final class Publisher implements Outlet {
    private final Flow.Publisher<?> publisher;

    public Publisher(Flow.Publisher<?> publisher) {
      this.publisher = publisher;
    }

    /**
     * The publisher.
     *
     * @return The publisher.
     */
    public Flow.Publisher<?> getPublisher() {
      return publisher;
    }
  }

  /**
   * A publisher of zero to many values.
   * <p>
   * When built, should produce a publisher that produces all the values (until cancelled) emitted by this iterables
   * iterator, followed by completion of the stream.
   * <p>
   * Any exceptions thrown by the iterator must be propagated downstream.
   */
  final class Of implements Outlet {
    private final java.lang.Iterable<?> elements;

    public Of(java.lang.Iterable<?> elements) {
      this.elements = elements;
    }

    /**
     * The elements to emit.
     *
     * @return The elements to emit.
     */
    public java.lang.Iterable<?> getElements() {
      return elements;
    }

    public static Of EMPTY = new Of(Collections.emptyList());
  }

  /**
   * A processor stage.
   * <p>
   * When built, should connect upstream of the graph to the inlet of this processor, and downstream to the outlet.
   */
  final class Processor implements Inlet, Outlet {
    private final Flow.Processor<?, ?> processor;

    public Processor(Flow.Processor<?, ?> processor) {
      this.processor = processor;
    }

    /**
     * The processor.
     *
     * @return The processor.
     */
    public Flow.Processor<?, ?> getProcessor() {
      return processor;
    }
  }

  /**
   * A subscriber stage that emits the first element encountered.
   * <p>
   * When built, the {@link java.util.concurrent.CompletionStage} should emit an {@link java.util.Optional} of the first
   * element emitted. If no element is emitted before completion of the stream, it should emit an empty optional. Once
   * the element has been emitted, the stream should be cancelled if not already complete.
   * <p>
   * If an error is emitted before the first element is encountered, the stream must redeem the completion stage with
   * that error.
   */
  final class FindFirst implements Inlet {
    private FindFirst() {
    }

    public static final FindFirst INSTANCE = new FindFirst();
  }

  /**
   * A subscriber.
   * <p>
   * When built, the {@link java.util.concurrent.CompletionStage} should emit <code>null</code> when the stream
   * completes normally, or an error if the stream terminates with an error.
   * <p>
   * Implementing this will typically require inserting a handler before the subscriber that listens for errors.
   */
  final class Subscriber implements Inlet {
    private final Flow.Subscriber<?> subscriber;

    public Subscriber(Flow.Subscriber<?> subscriber) {
      this.subscriber = subscriber;
    }

    /**
     * The subscriber.
     *
     * @return The subscriber.
     */
    public Flow.Subscriber<?> getSubscriber() {
      return subscriber;
    }
  }

  /**
   * A collect stage.
   * <p>
   * This should use the collectors supplier to create an accumulated value, and then the accumulator BiConsumer should
   * be used to accumulate the received elements in the value. Finally, the returned
   * {@link java.util.concurrent.CompletionStage} should be redeemed by value returned by the finisher function applied
   * to the accumulated value when the stream terminates normally, or should be redeemed with an error if the stream
   * terminates with an error.
   * <p>
   * If the collector throws an exception, the stream must be cancelled, and the
   * {@link java.util.concurrent.CompletionStage} must be redeemed with that error.
   */
  final class Collect implements Inlet {
    private final Collector<?, ?, ?> collector;

    public Collect(Collector<?, ?, ?> collector) {
      this.collector = collector;
    }

    /**
     * The collector.
     *
     * @return The collector.
     */
    public Collector<?, ?, ?> getCollector() {
      return collector;
    }
  }

  /**
   * A for each stage.
   * <p>
   * The for each stage should execute the given action on each element encountered in the stream. Furthermore, the
   * for each stage should materialize into a {@link CompletionStage} of {@link Void}, which is redeemed when the
   * stream completes, either successfully or with an error.
   */
  final class ForEach implements Inlet {
    private final Consumer<?> action;

    public ForEach(Consumer<?> action) {
      this.action = action;
    }

    /**
     * The action to consume each element.
     *
     * @return The action.
     */
    public Consumer<?> getAction() {
      return action;
    }
  }

  /**
   * A flat map stage.
   * <p>
   * The flat map stage should execute the given mapper on each element, and concatenate the publishers emitted by
   * the mapper function into the resulting stream.
   * <p>
   * The graph emitted by the mapper function is guaranteed to have an outlet but no inlet.
   * <p>
   * The engine must be careful to ensure only one publisher emitted by the mapper function is running at a time.
   */
  final class FlatMap implements Inlet, Outlet {
    private final Function<?, Graph> mapper;

    public FlatMap(Function<?, Graph> mapper) {
      this.mapper = mapper;
    }

    /**
     * The mapper function.
     *
     * @return The mapper function.
     */
    public Function<?, Graph> getMapper() {
      return mapper;
    }
  }

  /**
   * A flat map stage that emits and flattens {@link CompletionStage}.
   * <p>
   * The flat map stage should execute the given mapper on each element, and concatenate the values redeemed by the
   * {@link CompletionStage}'s emitted by the mapper function into the resulting stream.
   * <p>
   * The engine must be careful to ensure only one mapper function is executed at a time, with the next mapper function
   * not executing until the {@link CompletionStage} returned by the previous mapper function has been redeemed.
   */
  final class FlatMapCompletionStage implements Inlet, Outlet {
    private final Function<?, CompletionStage<?>> mapper;

    public FlatMapCompletionStage(Function<?, CompletionStage<?>> mapper) {
      this.mapper = mapper;
    }

    /**
     * The mapper function.
     *
     * @return The mapper function.
     */
    public Function<?, CompletionStage<?>> getMapper() {
      return mapper;
    }
  }

  /**
   * A flat map stage that emits and fattens {@link Iterable}.
   * <p>
   * The flat map stage should execute the given mapper on each element, and concatenate the iterables emitted by
   * the mapper function into the resulting stream.
   */
  final class FlatMapIterable implements Inlet, Outlet {
    private final Function<?, Iterable<?>> mapper;

    public FlatMapIterable(Function<?, Iterable<?>> mapper) {
      this.mapper = mapper;
    }

    /**
     * The mapper function.
     *
     * @return The mapper function.
     */
    public Function<?, Iterable<?>> getMapper() {
      return mapper;
    }
  }

  /**
   * A failed publisher.
   * <p>
   * When built, this should produce a publisher that immediately fails the stream with the passed in error.
   */
  final class Failed implements Outlet {
    private final Throwable error;

    public Failed(Throwable error) {
      this.error = error;
    }

    public Throwable getError() {
      return error;
    }
  }

  /**
   * Concatenate the given graphs together.
   * <p>
   * Each graph must have an outlet and no inlet.
   * <p>
   * The resulting publisher produced by the concat stage must emit all the elements from the first graph,
   * and once that graph emits a completion signal, it must then subscribe to and emit all the elements from
   * the second. If an error is emitted by the either graph, the error should be emitted from the resulting stream.
   * <p>
   * If processing terminates early while the first graph is still emitting, either due to that graph emitting an
   * error, or due to a cancellation signal from downstream, then the second graph must be subscribed to and cancelled.
   * This is to ensure that any hot publishers that may be backing the graphs are cleaned up.
   */
  final class Concat implements Outlet {
    private final Graph first;
    private final Graph second;

    public Concat(Graph first, Graph second) {
      this.first = validate(first);
      this.second = validate(second);
    }

    private static Graph validate(Graph graph) {
      if (graph.hasInlet() || !graph.hasOutlet()) {
        throw new IllegalArgumentException(
            "Concatenated graphs must have an outlet, but no inlet, but this graph does not: " + graph);
      }
      return graph;
    }

    public Graph getFirst() {
      return first;
    }

    public Graph getSecond() {
      return second;
    }
  }

}
