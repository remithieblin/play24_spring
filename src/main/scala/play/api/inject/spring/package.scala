package play.api.inject

import scala.reflect.ClassTag

package object spring {

  /**
   * Create a binding key for the given class.
   *
   * @see The [[play.api.inject.Module Module]] class for information on how to provide bindings.
   */
  def bind[T](clazz: Class[T]): BindingKey[T] = BindingKey(clazz)

  /**
   * Create a binding key for the given class.
   *
   * @see The [[play.api.inject.Module Module]] class for information on how to provide bindings.
   */
  def bind[T: ClassTag]: BindingKey[T] = BindingKey(implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]])

}