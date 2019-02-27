package storm;

import java.util.List;
import java.util.ArrayList;
import java.lang.Math;

import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;

public class CurveFit {

    private List<Double> xSet;
    private List<Double> ySet;
    private int MAX_POLYNOMIAL_ORDER = 3;
    private final WeightedObservedPoints obs = new WeightedObservedPoints();
    private PolynomialCurveFitter fitter;
    private PolynomialFunction polynomialFunction;
    public double err = 0.0;
    private double[] coeff;

    public CurveFit(List<Double> xSet, List<Double> ySet) {
        assert xSet.size() == ySet.size();
        this.xSet = xSet;
        this.ySet = ySet;
    }

    public CurveFit init(int power) {
        MAX_POLYNOMIAL_ORDER = power;
        fitter = PolynomialCurveFitter.create(MAX_POLYNOMIAL_ORDER);
        trainingCurve();
        computeError();
        return this;
    }


    private void trainingCurve() {
        for (int i = 0; i < this.xSet.size(); i++) {
            this.obs.add(xSet.get(i), ySet.get(i));
        }
        this.coeff = this.fitter.fit(obs.toList());
        polynomialFunction = new PolynomialFunction(this.coeff);
    }

    private double computeError() {
        for (int i = 0; i < this.xSet.size(); i++) {
            err += Math.abs(predict(xSet.get(i)) - ySet.get(i)) / ySet.get(i);
        }
        return err / this.xSet.size();
    }

    private void printCoeff() {
        for (int i = 0; i < coeff.length; i++) {
            System.out.println(coeff[i] + "\t");
        }
    }

    public double predict(double x) {
        double result = 0;
        for (int i = 0; i <= MAX_POLYNOMIAL_ORDER; i++) {
            result += coeff[i] * Math.pow(x, i);
        }

        return result;
    }

    public static void main(String args[]) {
        //use example
        List<Double> xSet = new ArrayList<>();
        List<Double> ySet = new ArrayList<>();
        double x[] = new double[]{5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 65, 70, 75, 80, 85, 90, 95};
        double y[] = new double[]{121.98, 119, 122, 112, 116, 130, 116, 142, 188, 209, 136, 650, 620, 527, 726, 643, 244, 387, 713};
        for (int i = 0; i < x.length; i++) {
            xSet.add(x[i]);
            ySet.add(y[i]);
        }

        CurveFit curveFit = new CurveFit(xSet, ySet);
        curveFit.trainingCurve();

        double a = curveFit.predict(10);
        System.out.println(a);
    }
}
