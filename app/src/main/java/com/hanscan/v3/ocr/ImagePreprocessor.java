package com.hanscan.v3.ocr;

import android.graphics.Bitmap;
import android.graphics.PointF;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class ImagePreprocessor {
    public static class Variant {
        public final String name; public final Bitmap bitmap;
        public Variant(String name, Bitmap bitmap){this.name=name;this.bitmap=bitmap;}
    }

    private ImagePreprocessor() {}

    public static List<Variant> buildVariants(Bitmap src) {
        List<Variant> out = new ArrayList<>();
        out.add(new Variant("original", src));
        Bitmap enhanced = enhance(src);
        out.add(new Variant("contrast+sharp", enhanced));
        Bitmap deskewed = autoDeskew(src);
        if (deskewed != null) out.add(new Variant("deskew", deskewed));
        Bitmap warped = autoPerspective(src);
        if (warped != null) {
            out.add(new Variant("perspective", warped));
            out.add(new Variant("perspective+enhance", enhance(warped)));
        }
        return out;
    }

    public static Bitmap enhance(Bitmap src) {
        Mat rgba = new Mat(); Utils.bitmapToMat(src, rgba);
        Mat lab = new Mat(); Imgproc.cvtColor(rgba, lab, Imgproc.COLOR_RGBA2RGB); Imgproc.cvtColor(lab, lab, Imgproc.COLOR_RGB2Lab);
        List<Mat> ch = new ArrayList<>(); Core.split(lab, ch);
        Imgproc.createCLAHE(2.0, new Size(8,8)).apply(ch.get(0), ch.get(0));
        Core.merge(ch, lab); Imgproc.cvtColor(lab, lab, Imgproc.COLOR_Lab2RGB);
        Mat blur = new Mat(); Imgproc.GaussianBlur(lab, blur, new Size(0,0), 2.0);
        Mat sharp = new Mat(); Core.addWeighted(lab, 1.55, blur, -0.55, 0, sharp);
        Imgproc.cvtColor(sharp, sharp, Imgproc.COLOR_RGB2RGBA);
        Bitmap out = Bitmap.createBitmap(sharp.cols(), sharp.rows(), Bitmap.Config.ARGB_8888); Utils.matToBitmap(sharp, out);
        rgba.release();lab.release();blur.release();sharp.release();for(Mat m:ch)m.release();
        return out;
    }

    public static Bitmap autoDeskew(Bitmap src) {
        Mat rgba = new Mat(); Utils.bitmapToMat(src, rgba);
        Mat gray = new Mat(), edges = new Mat(), lines = new Mat();
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY);
        Imgproc.GaussianBlur(gray, gray, new Size(3,3), 0);
        Imgproc.Canny(gray, edges, 60, 170);
        Imgproc.HoughLinesP(edges, lines, 1, Math.PI/180.0, 70, Math.max(40, src.getWidth()*0.12), 20);
        List<Double> angles = new ArrayList<>();
        for (int i=0;i<lines.rows();i++) {
            double[] v = lines.get(i,0);
            if (v == null || v.length < 4) continue;
            double a = Math.toDegrees(Math.atan2(v[3]-v[1], v[2]-v[0]));
            while (a > 90) a -= 180; while (a < -90) a += 180;
            if (Math.abs(a) <= 25) angles.add(a);
        }
        Bitmap result = null;
        if (angles.size() >= 3) {
            angles.sort(Double::compareTo);
            double median = angles.get(angles.size()/2);
            if (Math.abs(median) >= 1.5 && Math.abs(median) <= 20) {
                Point center = new Point(rgba.cols()/2.0, rgba.rows()/2.0);
                Mat M = Imgproc.getRotationMatrix2D(center, median, 1.0);
                Mat dst = new Mat();
                Imgproc.warpAffine(rgba, dst, M, rgba.size(), Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE, new Scalar(255));
                result = Bitmap.createBitmap(dst.cols(), dst.rows(), Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(dst, result);
                M.release(); dst.release();
            }
        }
        rgba.release(); gray.release(); edges.release(); lines.release();
        return result;
    }

    public static Bitmap autoPerspective(Bitmap src) {
        Mat rgba = new Mat(); Utils.bitmapToMat(src, rgba);
        Mat gray = new Mat(), blur = new Mat(), edges = new Mat();
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY);
        Imgproc.GaussianBlur(gray, blur, new Size(5,5), 0);
        Imgproc.Canny(blur, edges, 55, 160);
        Imgproc.dilate(edges, edges, Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(3,3)));
        List<MatOfPoint> contours = new ArrayList<>(); Mat hierarchy = new Mat();
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
        contours.sort((a,b)->Double.compare(Imgproc.contourArea(b), Imgproc.contourArea(a)));
        MatOfPoint2f best = null;
        double minArea = src.getWidth()*src.getHeight()*0.18;
        for (int i=0;i<Math.min(20, contours.size());i++) {
            MatOfPoint2f c2 = new MatOfPoint2f(contours.get(i).toArray());
            double peri = Imgproc.arcLength(c2,true);
            MatOfPoint2f approx = new MatOfPoint2f(); Imgproc.approxPolyDP(c2,approx,0.02*peri,true);
            if (approx.total()==4 && Math.abs(Imgproc.contourArea(approx))>minArea) { best=approx; c2.release(); break; }
            c2.release(); approx.release();
        }
        Bitmap result = null;
        if (best != null) {
            Point[] q = order(best.toArray());
            double w1=dist(q[0],q[1]),w2=dist(q[3],q[2]),h1=dist(q[0],q[3]),h2=dist(q[1],q[2]);
            int W=(int)Math.max(w1,w2),H=(int)Math.max(h1,h2);
            if(W>250&&H>160){
                MatOfPoint2f srcPts=new MatOfPoint2f(q);
                MatOfPoint2f dstPts=new MatOfPoint2f(new Point(0,0),new Point(W-1,0),new Point(W-1,H-1),new Point(0,H-1));
                Mat M=Imgproc.getPerspectiveTransform(srcPts,dstPts), dst=new Mat();
                Imgproc.warpPerspective(rgba,dst,M,new Size(W,H),Imgproc.INTER_CUBIC,Core.BORDER_REPLICATE,new Scalar(255));
                result=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);Utils.matToBitmap(dst,result);
                srcPts.release();dstPts.release();M.release();dst.release();
            }
            best.release();
        }
        rgba.release();gray.release();blur.release();edges.release();hierarchy.release();for(MatOfPoint c:contours)c.release();
        return result;
    }

    private static Point[] order(Point[] p){
        Point[] out=new Point[4];
        Arrays.sort(p,Comparator.comparingDouble(a->a.x+a.y)); out[0]=p[0]; out[2]=p[3];
        Point a=p[1],b=p[2]; if((a.x-a.y)>(b.x-b.y)){out[1]=a;out[3]=b;}else{out[1]=b;out[3]=a;} return out;
    }
    private static double dist(Point a,Point b){return Math.hypot(a.x-b.x,a.y-b.y);}
}
