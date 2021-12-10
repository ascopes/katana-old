package io.ascopes.katana.ap.utils;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Base class for an iterator implementation that exposes the ability to coerce into a spliterator
 * or stream.
 *
 * @param <E> the iterator element type.
 * @author Ashley Scopes
 * @since 0.0.1
 */
public abstract class KatanaIterator<E> implements Iterator<E> {

  /**
   * {@inheritDoc}
   */
  @Override
  public abstract boolean hasNext();

  /**
   * {@inheritDoc}
   */
  @Override
  public abstract E next() throws NoSuchElementException;

  /**
   * Get the spliterator characteristics.
   *
   * @return the characteristics bitfield.
   */
  public abstract int characteristics();

  /**
   * Get a spliterator for this iterator.
   *
   * @return the spliterator.
   */
  @SuppressWarnings("MagicConstant")
  public final Spliterator<E> spliterator() {
    return Spliterators.spliteratorUnknownSize(this, this.characteristics());
  }

  /**
   * Cast this iterator to a stream.
   *
   * @return the stream.
   */
  public final Stream<E> stream() {
    return StreamSupport.stream(this.spliterator(), false);
  }

  /**
   * Decorate a regular iterator to turn it into a Katana iterator.
   *
   * @param iterator the iterator to decorate.
   * @param <E>      the element type.
   * @return the decorated iterator.
   */
  public static <E> KatanaIterator<E> decorate(Iterator<E> iterator) {
    // TODO(ascopes): unit test.
    return new KatanaIterator<E>() {
      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public E next() throws NoSuchElementException {
        return iterator.next();
      }

      @Override
      public int characteristics() {
        return Spliterator.ORDERED;
      }
    };
  }

  protected static NoSuchElementException noMoreElementsException(String name) {
    return new NoSuchElementException("There are no more " + name + " to iterate over");
  }
}