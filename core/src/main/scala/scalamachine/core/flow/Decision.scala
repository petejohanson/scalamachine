package scalamachine.core
package flow

trait Decision {

  def name: String

  def apply(r: Resource): r.Result[Decision] 

  override def toString = "Decision(" + name + ")"    
}

