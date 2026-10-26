package org.example.historyinaglimpse;

/** Small SI-unit models: ideal horizontal tracks; gravity is balanced by support. */
final class NewtonPhysics {
    final int law;
    double mass = 2, force = 6, rightMass = 2;
    double time, x, velocity, rightX, rightVelocity, pulse, contact;
    boolean friction;
    NewtonPhysics(int law) { this.law = law; reset(); }
    void reset() { time=0; x=0; velocity=0; rightX=0; rightVelocity=0; pulse=0; contact=0; }
    void push() { pulse=.25; }
    void release() { reset(); contact=.6; }
    double netForce() {
        if(law==2) return force;
        if(law==3) return contact>1e-9 ? -6 : 0;
        double applied=pulse>1e-9 ? 4 : 0;
        return applied - (friction && (velocity>0 || applied>0) ? .8 : 0);
    }
    void step(double dt) {
        if(!Double.isFinite(dt) || dt<0) throw new IllegalArgumentException("Invalid timestep");
        while(dt>1e-10) {
            double h=Math.min(dt,.01);
            if(law==1 && pulse>1e-9) h=Math.min(h,pulse);
            if(law==3 && contact>1e-9) h=Math.min(h,contact);
            double acceleration=netForce()/(law==1 ? 1 : mass);
            if(law==1 && acceleration<0 && velocity+acceleration*h<0) {
                double stop=-velocity/acceleration;
                x+=velocity*stop+.5*acceleration*stop*stop; velocity=0;
            } else { x+=velocity*h+.5*acceleration*h*h; velocity+=acceleration*h; }
            if(law==3) {
                double ar=contact>1e-9 ? 6/rightMass : 0;
                rightX+=rightVelocity*h+.5*ar*h*h; rightVelocity+=ar*h;
                contact=Math.max(0,contact-h);
            }
            pulse=Math.max(0,pulse-h);time+=h;dt-=h;
        }
    }
}
