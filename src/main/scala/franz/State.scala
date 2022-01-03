package franz

import cats.{Monad, Semigroup}

case class State[S, A](run: S => (A, S)):
  def flatMap[B](f: A => State[S, B]): State[S, B] =
    State { s =>
      val (a, t) = run(s)
      f(a).run(t)
    }

  def map[B](f: A => B): State[S, B] =
    State { s =>
      val (a, t) = run(s)
      (f(a), t)
    }

end State

object State:
  def of[S, A](value: A) = State[S, A](in => (value, in))

  def combine[S : Semigroup, A](state: S, value: A) = State[S, A](left => (value, summon[Semigroup[S]].combine(state, left)))

end State
