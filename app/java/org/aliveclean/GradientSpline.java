package org.aliveclean;

/** Natural cubic spline (zero second derivative at both ends), in normalized UV units. */
final class GradientSpline {
    final float[] knots,coefficients;
    private final double[] span,upper,rhs,curvature;
    GradientSpline(float[] x){
        if(x.length<3)throw new IllegalArgumentException("Spline needs at least three knots");
        knots=x.clone();int n=x.length;
        coefficients=new float[(n-1)*4];span=new double[n-1];upper=new double[n];rhs=new double[n];curvature=new double[n];
        for(int i=0;i<n;i++){
            if(!Float.isFinite(x[i])||(i>0&&x[i]<=x[i-1]))throw new IllegalArgumentException("Spline knots must increase");
            if(i>0)span[i-1]=x[i]-x[i-1];
        }
    }
    void interpolate(float[] y){
        int n=knots.length-1;
        if(y.length!=knots.length)throw new IllegalArgumentException("Spline dimensions");
        // Thomas solve for half the second derivative, with natural endpoints.
        for(int i=1;i<n;i++){
            double diagonal=2*(span[i-1]+span[i])-span[i-1]*upper[i-1];
            upper[i]=span[i]/diagonal;
            rhs[i]=(3*(((double)y[i+1]-y[i])/span[i]-((double)y[i]-y[i-1])/span[i-1])-span[i-1]*rhs[i-1])/diagonal;
        }
        for(int i=n-1;i>=0;i--){
            curvature[i]=rhs[i]-upper[i]*curvature[i+1];
            int offset=i*4;
            coefficients[offset]=y[i];
            coefficients[offset+1]=(float)(((double)y[i+1]-y[i])/span[i]-span[i]*(curvature[i+1]+2*curvature[i])/3);
            coefficients[offset+2]=(float)curvature[i];
            coefficients[offset+3]=(float)((curvature[i+1]-curvature[i])/(3*span[i]));
        }
    }
}
