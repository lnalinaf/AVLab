package ru.barsic.avlab.graphics;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import android.content.Context;
import android.graphics.*;
import android.util.DisplayMetrics;
import android.view.*;
import ru.barsic.avlab.basic.TouchListener;
import ru.barsic.avlab.basic.World;
import ru.barsic.avlab.helper.ScalingUtil;

public class DrawView extends SurfaceView implements SurfaceHolder.Callback {

	//======================== public static fields ========================

	public static Point position = new Point(0, 0);
	public static int width;
	public static int height;
	public static int maxZIndex = 100000;
	public static int currentZIndex = 0;
	public static ArrayList<Painter> painters = new ArrayList<>();
	public static Timer timer = new Timer("calculation timer");

	//======================== private static fields ========================

	private static final int PAINTING_DELAY = 60;

	private static DrawView drawViewInstance;

	private static final Comparator<Painter> Z_COMPARATOR = new Comparator<Painter>() {
		@Override
		public int compare(Painter v1, Painter v2) {
			return v1.getZIndex() < v2.getZIndex() ? -1 : 1;
		}
	};

	//======================== private instance fields ========================

	DrawRunnable drawRunnable;
	final TouchListener touchListener;

	//======================== public static methods ========================

	public static DrawView createInstance(Context context) {
		drawViewInstance = new DrawView(context);

		return drawViewInstance;
	}

	public static DrawView getInstance() {
		if (drawViewInstance == null)
			throw new IllegalStateException("Instance is not created");
		return drawViewInstance;
	}

	public static void sortByZ() {
		Collections.sort(painters, Z_COMPARATOR);
	}

	public static void returnPrimaryPosition() {
		for (Painter vi : painters) {
			if (vi.getHolder() == null) {
				vi.moveBy(-position.x, -position.y);
			}
		}
		position.x = 0;
		position.y = 0;
	}

	public static void moveVisibleArea(int dx, int dy) {
		position.x += dx;
		position.y += dy;
		World.deviceY -= dy / ScalingUtil.pixToSmY;
		World.deviceX -= dx / ScalingUtil.pixToSmX;
		System.out.println("WORLD x:" + World.deviceX + ", y:" + World.deviceY);
		for (Painter vi : painters) {
			if (vi.getHolder() == null) {
				vi.moveBy(dx, dy);
			}
		}
	}

	//======================== public instance methods  ========================

	@Override
	public void surfaceCreated(SurfaceHolder surfaceHolder) {
		drawRunnable = new DrawRunnable(getHolder());
		Thread thread = new Thread();
		drawRunnable.setRunning(true);
		Executor executor = Executors.newSingleThreadExecutor();
		executor.execute(drawRunnable);
	}

	@Override
	public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i2, int i3) {

	}

	@Override
	public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
		boolean retry = true;
		drawRunnable.setRunning(false);
		while (retry) {
			try {
				System.out.println("98597345897345897348957348957348957");
				//drawRunnable.join();
				//todo: разобраться!
				retry = false;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	//======================== private implementation ========================

	private DrawView(Context context) {
		super(context);
		getHolder().addCallback(this);
		DisplayMetrics displaymetrics = getResources().getDisplayMetrics();
		ScalingUtil.pixToSmX = displaymetrics.xdpi / ScalingUtil.INCH_TO_SM;
		ScalingUtil.pixToSmY = displaymetrics.ydpi / ScalingUtil.INCH_TO_SM;
		World.deviceHeightSm = displaymetrics.heightPixels / ScalingUtil.pixToSmY;
		World.deviceWidthSm = displaymetrics.widthPixels / ScalingUtil.pixToSmX;
		this.touchListener = new TouchListener(displaymetrics.heightPixels, displaymetrics.widthPixels);
		//this.setBackgroundColor(Color.WHITE);
		this.setOnTouchListener(touchListener);
	}

	//======================== DrawThread ========================

	class DrawRunnable implements Runnable {

		private volatile boolean running = false;
		private final SurfaceHolder surfaceHolder;

		public DrawRunnable(SurfaceHolder surfaceHolder) {
			this.surfaceHolder = surfaceHolder;
		}

		public void setRunning(boolean running) {
			this.running = running;
		}

		@Override
		public void run() {
			Canvas canvas;
			while (running) {
				canvas = null;
				try {
					canvas = surfaceHolder.lockCanvas(null);
					if (canvas == null)
						continue;

					canvas.drawColor(Color.GREEN);
					Paint paint = new Paint();
					paint.setColor(Color.BLACK);
					paint.setStyle(Paint.Style.STROKE);
					//canvas.drawRect(3, 3, ScalingUtil.getWorldWidthInPix() - 3, ScalingUtil.getWorldHeightInPix() - 3, paint);
					double[] intersect = intersectScreenAndWorldBounds();
					if (intersect != null) {
						if (intersect[0] == intersect[2] || intersect[1] == intersect[3]) {
							canvas.drawLine((float)intersect[0], (float)intersect[1], (float)intersect[2], (float)intersect[3], paint);
						} else {
							canvas.drawLine((float)intersect[0],(float)intersect[1], (float)intersect[0], (float)intersect[3], paint);
							canvas.drawLine((float)intersect[0],(float)intersect[3], (float)intersect[2], (float)intersect[3], paint);
						}
					}

					//canvas.drawRect(3 , 3, touchListener.screenWidth - 3, touchListener.screenHeight - 3, paint);
					for (Painter painter : painters)
						painter.onDraw(canvas);
				} finally {
					if (canvas != null)
						surfaceHolder.unlockCanvasAndPost(canvas);
				}
				try {
					Thread.sleep(PAINTING_DELAY);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private double[] intersectScreenAndWorldBounds() {
		double deviceBRX = World.deviceX + World.deviceWidthSm;
		double deviceBRY = World.deviceY + World.deviceHeightSm;
		double[] result;
		result = intersectVerticalBounds(deviceBRX, deviceBRY, 0);
		if (result != null)
			return result;
		System.out.println(" * 1 * ");
		result = intersectVerticalBounds(deviceBRX, deviceBRY, World.WORLD_WIDTH);
		if (result != null)
			return result;
		System.out.println(" * 2 * ");
		result = intersectHorizontalBounds(deviceBRX, deviceBRY, 0);
		System.out.println("****** " + Arrays.toString(result));
		if (result != null)
			return result;
		System.out.println(" * 3 * ");
		return intersectHorizontalBounds(deviceBRX, deviceBRY, World.WORLD_HEIGHT);
	}

	private double[] intersectVerticalBounds(double deviceBRX, double deviceBRY, double verticalBoundX) {
		double intersectX1 = 0;
		double intersectY1 = 0;
		double intersectY2 = 0;
		double intersectX2 = 0;
		if ((World.deviceX < verticalBoundX && deviceBRX > verticalBoundX) && (deviceBRY > 0 && World.deviceY < World.WORLD_HEIGHT)) {
			intersectX1 = World.deviceWidthSm - (deviceBRX - verticalBoundX);

			if (World.deviceY >= 0 && deviceBRY <= World.WORLD_HEIGHT) {
				intersectY1 = 0;
				intersectX2 = intersectX1;
				intersectY2 = World.deviceHeightSm;
				System.out.println("0 **** = " + intersectX1 + ", " + intersectY1 + ", " + intersectX2 + ", " + intersectY2);

			} else if (World.deviceY > 0 && deviceBRY > World.WORLD_HEIGHT) {
				intersectY1 = 0;
				intersectY2 = World.deviceHeightSm - (deviceBRY - World.WORLD_HEIGHT);
				//intersectX1 = World.deviceWidthSm - deviceBRX;
				intersectX2 = verticalBoundX == 0 ? World.deviceWidthSm : 0;
				System.out.println("1 **** = " + intersectX1 + ", " + intersectY1 + ", " + intersectX2 + ", " + intersectY2);
			} else {
				intersectY1 = World.deviceHeightSm;
				intersectY2 = World.deviceHeightSm - deviceBRY;
				intersectX2 = verticalBoundX == 0 ? World.deviceWidthSm : 0;
				System.out.println("2 **** = " + intersectX1 + ", " + intersectY1 + ", " + intersectX2 + ", " + intersectY2);

			}
			return new double[]{ScalingUtil.scalingRealSizeX(intersectX1),ScalingUtil.scalingRealSizeY(intersectY1),ScalingUtil.scalingRealSizeX(intersectX2), ScalingUtil.scalingRealSizeY(intersectY2)};
		}
		return null;
	}
	private double[] intersectHorizontalBounds(double deviceBRX, double deviceBRY, double horizontalBoundY){
		double intersectX1 = 0;
		double intersectY1 = 0;
		double intersectY2 = 0;
		double intersectX2 = 0;
		if ((World.deviceX > 0 && deviceBRX < World.WORLD_WIDTH) && (World.deviceY < horizontalBoundY && deviceBRY > horizontalBoundY))  {
			intersectY1 = World.deviceHeightSm - (deviceBRY - horizontalBoundY);
			intersectY2 = intersectY1;
			intersectX1 = 0;
			intersectX2 = World.deviceWidthSm;

		return new double[]{ScalingUtil.scalingRealSizeX(intersectX1),ScalingUtil.scalingRealSizeY(intersectY1),ScalingUtil.scalingRealSizeX(intersectX2), ScalingUtil.scalingRealSizeY(intersectY2)};
	}
		return  null;
	}

}
