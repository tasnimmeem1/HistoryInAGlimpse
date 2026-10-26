package org.example.historyinaglimpse;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NewtonPhysicsTests {
 @Test void briefPushThenZeroForcePreservesVelocity(){var p=new NewtonPhysics(1);p.push();p.step(.25);assertEquals(1,p.velocity,1e-8);p.step(2);assertEquals(1,p.velocity,1e-8);assertEquals(2.125,p.x,1e-8);}
 @Test void frictionStopsWithoutReversingTheBall(){var p=new NewtonPhysics(1);p.push();p.step(.25);p.friction=true;p.step(10);assertEquals(0,p.velocity,1e-8);assertEquals(.75,p.x,1e-8);}
 @Test void constantForceUsesMassAndAnalyticalDisplacement(){var p=new NewtonPhysics(2);p.mass=3;p.force=12;p.step(2);assertEquals(8,p.velocity,1e-8);assertEquals(8,p.x,1e-8);}
 @Test void zeroForceDoesNotStartCart(){var p=new NewtonPhysics(2);p.force=0;p.step(5);assertEquals(0,p.x,1e-8);assertEquals(0,p.velocity,1e-8);}
 @Test void unequalMassesReceiveEqualOppositeImpulse(){var p=new NewtonPhysics(3);p.mass=2;p.rightMass=4;p.release();p.step(.6);assertEquals(-1.8,p.velocity,1e-8);assertEquals(.9,p.rightVelocity,1e-8);assertEquals(0,p.mass*p.velocity+p.rightMass*p.rightVelocity,1e-8);p.step(3);assertEquals(-1.8,p.velocity,1e-8);assertEquals(.9,p.rightVelocity,1e-8);}
 @Test void ResultsDoNotDependOnFrameStep(){var a=new NewtonPhysics(2);var b=new NewtonPhysics(2);for(int n=0;n<200;n++)a.step(.01);b.step(2);assertEquals(a.x,b.x,1e-8);assertEquals(a.velocity,b.velocity,1e-8);}
}
