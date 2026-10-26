package org.example.historyinaglimpse;

/** Timed teaching sequence, not a thermodynamic or efficiency calculation. */
final class WattCycle {
    double elapsed;
    void step(double seconds,double speed){if(seconds>0)elapsed+=seconds*speed;}
    void reset(){elapsed=0;}
    double fraction(){return (elapsed%8)/8;}
    int stage(){double p=fraction();return p<.25?0:p<.5?1:p<.8?2:3;}
    double piston(){double p=fraction();return p<.25?1-ease(p/.25):p<.5?0:p<.8?ease((p-.5)/.3):1;}
    private static double ease(double x){return x*x*(3-2*x);}
}
