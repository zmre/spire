package spire.math.poly

import compat._
import scala.annotation.tailrec
import scala.reflect._
import scala.{specialized => spec}
import spire.algebra._
import spire.implicits._
import spire.math._


// A monomial is the product of a coefficient and a list of variables each to a non-negative integer power.
case class Monomial[@spec(Float, Double) C](coeff: C, exps: Array[Int])
                                           (implicit val ct: ClassTag[C]) { lhs =>
  require(exps.forall(_ >= 0), "Monomial variable exponents must be non-negative!")

  lazy val degree: Int = {
    var sum = 0
    cfor(0)(_ < exps.length , _ + 1) { i => sum += exps(i) }
    sum
  }

  def isZero(implicit r: Semiring[C], eq: Eq[C]): Boolean =
    coeff === r.zero

  def eval(vars: Array[C])(implicit r: Semiring[C]): C = {
    var prod = coeff
    cfor(0)(_ < exps.length , _ + 1) { i => prod *= (vars(i) ** exps(i)) }
    prod
  }

  def unary_-(implicit r: Rng[C]): Monomial[C] = 
    Monomial(-coeff, exps)

  def +(rhs: Monomial[C])(implicit r: Semiring[C], eq: Eq[C]): Monomial[C] =
    Monomial(lhs.coeff + rhs.coeff, sumExponents(lhs.exps, rhs.exps))

  def *(rhs: Monomial[C])(implicit r: Semiring[C]): Monomial[C] =
    Monomial(lhs.coeff * rhs.coeff, sumExponents(lhs.exps, rhs.exps))

  def divideBy(x: C)(implicit f: Field[C]): Monomial[C] =
    Monomial(coeff / x, lhs.exps)

  def /(rhs: Monomial[C])(implicit r: Field[C]): Monomial[C] = {
    Monomial(lhs.coeff / rhs.coeff, subtractExponentsAbsolute(lhs.exps, rhs.exps))
  }

  // The leading variable index, i.e. if x^0yz^2 = 1
  // & if x^0y^0z^2 = 2 (basically the index of the first term with a non-zero exponent) 
  def firstNonZeroVarIndex(a: Array[Int] = lhs.exps): Int = {
    var i = 0
    while((a(i) <= 0) && (i < a.length)) { i += 1 } 
    i
  }

  def divides(rhs: Monomial[C])(implicit f: Field[C], o: Order[C]): Boolean = {
    @tailrec def divides_(l: Array[Int], r: Array[Int]): Boolean = {
      if(l.isEmpty) true else if(r.isEmpty) false else
        firstNonZeroVarIndex(l) compare firstNonZeroVarIndex(r) match {
          case -1 => false
          case 1 => divides_(l, if(r.tail.isEmpty || r.head == 0) Array() else if(r.tail.isEmpty) Array(r.head) else r.tail)
          case 0 => if(l(firstNonZeroVarIndex(l)) <= r(firstNonZeroVarIndex(r))) divides_(
            if(l.tail.isEmpty || l.head == 0) Array() else if(l.tail.isEmpty) Array(l.head) else l.tail,
            if(r.tail.isEmpty || r.head == 0) Array() else if(r.tail.isEmpty) Array(r.head) else r.tail) else false
        }
    }
    lhs.coeff <= rhs.coeff && divides_(lhs.exps, rhs.exps)
  }

  def gcd(rhs: Monomial[C])(implicit f: Field[C]): Monomial[C] = ???
  // This Haskell algorithm is outputting the wrong value... hmm 
  //   @tailrec def gcd_(s: C, zks: List[Int], l: List[Int], r: List[Int]): Monomial[C] = {
  //     if(l.isEmpty && r.isEmpty) Monomial(s, zks.reverse.toArray) else {
  //       val i = firstNonZeroVarIndex(l.toArray)
  //       val j = firstNonZeroVarIndex(r.toArray)
  //        i compare j match {
  //         case -1 => gcd_(s, zks, l.tail, r)
  //         case 1 => gcd_(s, zks, l, r.tail)
  //         case 0 => {
  //           val k = min(l(i), r(j))
  //           gcd_(s + f.fromInt(k), k :: zks, l.tail, r.tail)
  //         }
  //       }
  //     }
  //   }
  //   gcd_(f.zero, Nil, lhs.exps.toList, rhs.exps.toList)
  // }

  def lcm(rhs: Monomial[C])(implicit f: Field[C]): Monomial[C] = ???

  def coprime(rhs: Monomial[C])(implicit f: Field[C]): Boolean = {
    @tailrec def coprime_(l: Array[Int], r: Array[Int]): Boolean = {
      if(l.isEmpty && r.isEmpty) true else {
        val i = firstNonZeroVarIndex(l)
        val j = firstNonZeroVarIndex(r)
        i compare j match {
          case -1 => coprime_(l, (j :: r.toList).toArray)
          case 1 => coprime_((i :: l.toList).toArray, r)
          case 0 => false
        }
      }
    }
    coprime_(lhs.exps, rhs.exps)
  }

  private final def sumExponents(l: Array[Int], r: Array[Int]): Array[Int] = {
    if (l.length < r.length) {
      sumExponents(r, l)
    } else {
      val newExps = new Array[Int](l.length)
      cfor(0)(_ < r.length, _ + 1) { i =>
        newExps(i) = l(i) + r(i)
      }
      cfor(r.length)(_ < l.length, _ + 1) { i =>
        newExps(i) = l(i)
      }
      newExps
    }
  }

  private final def subtractExponentsAbsolute(l: Array[Int], r: Array[Int]): Array[Int] = {
    if (l.length < r.length) {
      subtractExponentsAbsolute(r, l)
    } else {
      val newExps = new Array[Int](l.length)
      cfor(0)(_ < r.length, _ + 1) { i =>
        newExps(i) = abs(l(i) - r(i))
      }
      cfor(r.length)(_ < l.length, _ + 1) { i =>
        newExps(i) = l(i)
      }
      newExps
    }
  }

  override def toString = {
    import Monomial._

    // At the moment, this is obviously limiting the number of variables
    // to three. It still works, but using a scalaz style Enum[String] where we 
    // start with 'x' then we go 'x'.succ for subsequent variables would be preferable.
    def expString(e: Int, strRec: String = "") : String = 
      if (e < exps.length) {
        lazy val varStr = e match {
          case 0 => "x"
          case 1 => "y"
          case _ => "z"
        }
        exps(e) match {
          case 0 => expString(e + 1, strRec)
          case 1 => expString(e + 1, s"$strRec$varStr")
          case exp => expString(e + 1, s"$strRec$varStr^$exp")
        }
      } else strRec

    def simpleCoeff: Option[String] = coeff match {
      case 0 => Some("")
      case 1 if exps(0) == 0 => Some(s" + $coeff${expString(0)}")
      case 1 => Some(s" + ${expString(0)}")
      case -1 if exps(0) != 0 => Some(s" - ${expString(0)}")
      case _ => None
    }

    def stringCoeff: Option[String] = coeff.toString match {
      case IsZero() => Some("")
      case IsNegative(posPart) => Some(s" - $posPart${expString(0)}")
      case _ => None
    }

    simpleCoeff orElse stringCoeff getOrElse s" + $coeff${expString(0)}"

  }

}

object Monomial {

  def zero[@spec(Float, Double) C: ClassTag](implicit r: Rig[C]): Monomial[C] =
    Monomial(r.zero, Array())
  
  def one[@spec(Float, Double) C: ClassTag](implicit r: Rig[C]): Monomial[C] = 
    Monomial(r.one, Array(1))

  private val IsZero = "0".r
  private val IsNegative = "-(.*)".r

}


trait MonomialEq[@spec(Float, Double) C] extends Eq[Monomial[C]] {
  implicit def scalar: Semiring[C]
  implicit def eq: Eq[C]
  implicit def ct: ClassTag[C]

  def eqv(x: Monomial[C], y: Monomial[C]): Boolean =
    x.exps === y.exps
}


