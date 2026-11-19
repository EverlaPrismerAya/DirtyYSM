package net.everla.ysmPhysLib;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.List;

/** Small numerical routines shared by the solver and its headless checks. */
public final class PhysMath {
    private PhysMath() {}

    /** Horn's least-squares rotation, using a symmetric Jacobi eigensolve. */
    public static Quaternionf fit(List<Vector3f> from, List<Vector3f> to, Quaternionf fallback) {
        if (from.isEmpty()) return new Quaternionf(fallback);
        if (from.size() == 1) {
            if (from.get(0).lengthSquared() < 1e-12f || to.get(0).lengthSquared() < 1e-12f)
                return new Quaternionf(fallback);
            return new Quaternionf().rotationTo(from.get(0), to.get(0));
        }
        // Collinear branches have no uniquely defined twist.
        boolean noncollinear = false, targetNoncollinear = false;
        for (int i = 1; i < from.size(); i++) {
            noncollinear |= new Vector3f(from.get(0)).cross(from.get(i)).lengthSquared() > 1e-12f;
            targetNoncollinear |= new Vector3f(to.get(0)).cross(to.get(i)).lengthSquared() > 1e-12f;
        }
        if (!noncollinear || !targetNoncollinear) return new Quaternionf(fallback);
        double[][] h = new double[3][3];
        for (int k = 0; k < from.size(); k++)
            for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++)
                h[i][j] += from.get(k).get(i) * to.get(k).get(j);
        double xx=h[0][0], xy=h[0][1], xz=h[0][2], yx=h[1][0], yy=h[1][1], yz=h[1][2], zx=h[2][0], zy=h[2][1], zz=h[2][2];
        double[][] n = {{xx+yy+zz,yz-zy,zx-xz,xy-yx},
                {yz-zy,xx-yy-zz,xy+yx,zx+xz},
                {zx-xz,xy+yx,-xx+yy-zz,yz+zy},
                {xy-yx,zx+xz,yz+zy,-xx-yy+zz}};
        double[][] v = new double[4][4];
        for(int i=0;i<4;i++) v[i][i]=1;
        for(int sweep=0;sweep<16;sweep++) for(int p=0;p<4;p++) for(int q=p+1;q<4;q++) {
            if(Math.abs(n[p][q])<1e-14) continue;
            double angle=.5*Math.atan2(2*n[p][q], n[q][q]-n[p][p]);
            double c=Math.cos(angle), s=Math.sin(angle);
            double pp=n[p][p], qq=n[q][q], pq=n[p][q];
            n[p][p]=c*c*pp-2*s*c*pq+s*s*qq;
            n[q][q]=s*s*pp+2*s*c*pq+c*c*qq;
            n[p][q]=n[q][p]=0;
            for(int k=0;k<4;k++) {
                if(k!=p && k!=q) {
                    double kp=n[k][p], kq=n[k][q];
                    n[k][p]=n[p][k]=c*kp-s*kq;
                    n[k][q]=n[q][k]=s*kp+c*kq;
                }
                double vp=v[k][p], vq=v[k][q];
                v[k][p]=c*vp-s*vq; v[k][q]=s*vp+c*vq;
            }
        }
        int best=0; for(int i=1;i<4;i++) if(n[i][i]>n[best][best]) best=i;
        Quaternionf result = new Quaternionf((float)v[1][best],(float)v[2][best],(float)v[3][best],(float)v[0][best]).normalize();
        return result.isFinite() ? result : new Quaternionf(fallback);
    }

    public static Quaternionf limit(Quaternionf rotation, float degrees) {
        Quaternionf q = new Quaternionf(rotation).normalize();
        if (q.w < 0) q.set(-q.x,-q.y,-q.z,-q.w);
        float angle = 2*(float)Math.acos(Math.min(1,q.w));
        float max = (float)Math.toRadians(degrees);
        return angle > max && angle > 1e-6f ? new Quaternionf().slerp(q,max/angle) : q;
    }

    /** Inverse of rotationZYX; JOML 1.10.5's getEulerAnglesZYX has an incorrect X denominator. */
    public static Vector3f eulerZYX(Quaternionf q) {
        float sinY=2*(q.w*q.y-q.z*q.x);
        if(Math.abs(sinY)>=.999999f)
            return new Vector3f(2*(float)Math.atan2(q.x,q.w),Math.copySign((float)Math.PI/2,sinY),0);
        return new Vector3f((float)Math.atan2(2*(q.w*q.x+q.y*q.z),1-2*(q.x*q.x+q.y*q.y)),
                (float)Math.asin(Math.max(-1,Math.min(1,sinY))),
                (float)Math.atan2(2*(q.w*q.z+q.x*q.y),1-2*(q.y*q.y+q.z*q.z)));
    }

    /** Closest point on triangle (including edges/corners), Ericson region tests. */
    public static Vector3f closest(Vector3f p, Vector3f a, Vector3f b, Vector3f c) {
        Vector3f ab=new Vector3f(b).sub(a), ac=new Vector3f(c).sub(a), ap=new Vector3f(p).sub(a);
        float d1=ab.dot(ap),d2=ac.dot(ap);
        if(d1<=0 && d2<=0) return new Vector3f(a);
        Vector3f bp=new Vector3f(p).sub(b);
        float d3=ab.dot(bp),d4=ac.dot(bp);
        if(d3>=0 && d4<=d3) return new Vector3f(b);
        float vc=d1*d4-d3*d2;
        if(vc<=0 && d1>=0 && d3<=0) return new Vector3f(a).fma(d1/(d1-d3),ab);
        Vector3f cp=new Vector3f(p).sub(c);
        float d5=ab.dot(cp),d6=ac.dot(cp);
        if(d6>=0 && d5<=d6) return new Vector3f(c);
        float vb=d5*d2-d1*d6;
        if(vb<=0 && d2>=0 && d6<=0) return new Vector3f(a).fma(d2/(d2-d6),ac);
        float va=d3*d6-d5*d4;
        if(va<=0 && d4-d3>=0 && d5-d6>=0)
            return new Vector3f(b).fma((d4-d3)/((d4-d3)+(d5-d6)),new Vector3f(c).sub(b));
        float sum=va+vb+vc;
        if(Math.abs(sum)<1e-15f) return new Vector3f(a);
        return new Vector3f(a).fma(vb/sum,ab).fma(vc/sum,ac);
    }
}
