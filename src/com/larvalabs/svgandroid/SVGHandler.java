package com.larvalabs.svgandroid;

import java.util.ArrayList;
import java.util.Stack;

import org.anddev.andengine.util.Debug;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Picture;
import android.graphics.RectF;

import com.larvalabs.svgandroid.adt.SVGProperties;
import com.larvalabs.svgandroid.adt.SVGPaint;
import com.larvalabs.svgandroid.adt.gradient.Gradient;
import com.larvalabs.svgandroid.adt.gradient.Gradient.Stop;
import com.larvalabs.svgandroid.util.NumberParser;
import com.larvalabs.svgandroid.util.NumberParser.NumberParserResult;
import com.larvalabs.svgandroid.util.PathParser;
import com.larvalabs.svgandroid.util.SAXHelper;
import com.larvalabs.svgandroid.util.TransformParser;

/**
 * @author Larva Labs, LLC
 * @author Nicolas Gramlich
 * @since 16:50:02 - 21.05.2011
 */
public class SVGHandler extends DefaultHandler {
	// ===========================================================
	// Constants
	// ===========================================================

	// ===========================================================
	// Fields
	// ===========================================================

	private final Picture mPicture;
	private Canvas mCanvas;
	private final Paint mPaint = new Paint();
	private final RectF mRect = new RectF();
	private RectF mBounds;
	private final RectF mLimits = new RectF(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
	
	private Gradient mCurrentGradient;

	private boolean mBoundsMode;

	private boolean mHidden;
	private int mHiddenLevel;
	private final Stack<Boolean> mGroupTransformStack = new Stack<Boolean>();
	private final SVGPaint mSVGPaint = new SVGPaint(this.mPaint);

	// ===========================================================
	// Constructors
	// ===========================================================

	public SVGHandler(final Picture pPicture) {
		this.mPicture = pPicture;
		this.mPaint.setAntiAlias(true);
	}

	// ===========================================================
	// Getter & Setter
	// ===========================================================

	public RectF getBounds() {
		return this.mBounds;
	}

	public RectF getLimits() {
		return this.mLimits;
	}

	// ===========================================================
	// Methods for/from SuperClass/Interfaces
	// ===========================================================

	@Override
	public void startElement(final String pNamespace, final String pLocalName, final String pQualifiedName, final Attributes pAttributes) throws SAXException {
		/* Ignore everything but rectangles in bounds mode. */
		if (this.mBoundsMode) {
			if (pLocalName.equals("rect")) {
				final float x = SVGParser.getFloatAttribute(pAttributes, "x", 0f);
				final float y = SVGParser.getFloatAttribute(pAttributes, "y", 0f);
				final float width = SVGParser.getFloatAttribute(pAttributes, "width", 0f);
				final float height = SVGParser.getFloatAttribute(pAttributes, "height", 0f);
				this.mBounds = new RectF(x, y, x + width, y + height);
			}
			return;
		}
		if (pLocalName.equals("svg")) {
			final int width = (int) Math.ceil(SVGParser.getFloatAttribute(pAttributes, "width", 0f));
			final int height = (int) Math.ceil(SVGParser.getFloatAttribute(pAttributes, "height", 0f));
			this.mCanvas = this.mPicture.beginRecording(width, height);
		} else if (pLocalName.equals("defs")) {
			// Ignore
		} else if (pLocalName.equals("linearGradient")) {
			this.mCurrentGradient = mSVGPaint.registerGradient(pAttributes, true);
		} else if (pLocalName.equals("radialGradient")) {
			this.mCurrentGradient = mSVGPaint.registerGradient(pAttributes, false);
		} else if (pLocalName.equals("stop")) {
			final Stop gradientStop = mSVGPaint.parseGradientStop(pAttributes);
			this.mCurrentGradient.addStop(gradientStop);
		} else if (pLocalName.equals("g")) {
			// Check to see if this is the "bounds" layer
			if ("bounds".equalsIgnoreCase(SAXHelper.getStringAttribute(pAttributes, "id"))) {
				this.mBoundsMode = true;
			}

			this.mGroupTransformStack.push(this.pushTransform(pAttributes));

			if (this.mHidden) {
				this.mHiddenLevel++;
			}
			// Go in to hidden mode if display is "none"
			if ("none".equals(SAXHelper.getStringAttribute(pAttributes, "display"))) {
				if (!this.mHidden) {
					this.mHidden = true;
					this.mHiddenLevel = 1;
				}
			}
		} else if (!this.mHidden && pLocalName.equals("rect")) {
			final float x = SVGParser.getFloatAttribute(pAttributes, "x", 0f);
			final float y = SVGParser.getFloatAttribute(pAttributes, "y", 0f);
			final float width = SVGParser.getFloatAttribute(pAttributes, "width", 0f);
			final float height = SVGParser.getFloatAttribute(pAttributes, "height", 0f);
			final boolean pushed = this.pushTransform(pAttributes);
			final SVGProperties svgProperties = new SVGProperties(pAttributes);
			if (this.setFill(svgProperties)) {
				this.setLimits(x, y, width, height);
				this.mCanvas.drawRect(x, y, x + width, y + height, this.mPaint);
			}
			if (this.setStroke(svgProperties)) {
				this.mCanvas.drawRect(x, y, x + width, y + height, this.mPaint);
			}
			if(pushed) {
				this.popTransform();
			}
		} else if (!this.mHidden && pLocalName.equals("line")) {
			final float x1 = SVGParser.getFloatAttribute(pAttributes, "x1", 0f);
			final float x2 = SVGParser.getFloatAttribute(pAttributes, "x2", 0f);
			final float y1 = SVGParser.getFloatAttribute(pAttributes, "y1", 0f);
			final float y2 = SVGParser.getFloatAttribute(pAttributes, "y2", 0f);
			final SVGProperties sVGProperties = new SVGProperties(pAttributes);
			if (this.setStroke(sVGProperties)) {
				final boolean pushed = this.pushTransform(pAttributes);
				this.setLimits(x1, y1);
				this.setLimits(x2, y2);
				this.mCanvas.drawLine(x1, y1, x2, y2, this.mPaint);
				if(pushed) {
					this.popTransform();
				}
			}
		} else if (!this.mHidden && pLocalName.equals("circle")) {
			final Float centerX = SVGParser.getFloatAttribute(pAttributes, "cx");
			final Float centerY = SVGParser.getFloatAttribute(pAttributes, "cy");
			final Float radius = SVGParser.getFloatAttribute(pAttributes, "r");
			if (centerX != null && centerY != null && radius != null) {
				final boolean pushed = this.pushTransform(pAttributes);
				final SVGProperties sVGProperties = new SVGProperties(pAttributes);
				if (this.setFill(sVGProperties)) {
					this.setLimits(centerX - radius, centerY - radius);
					this.setLimits(centerX + radius, centerY + radius);
					this.mCanvas.drawCircle(centerX, centerY, radius, this.mPaint);
				}
				if (this.setStroke(sVGProperties)) {
					this.mCanvas.drawCircle(centerX, centerY, radius, this.mPaint);
				}
				if(pushed) {
					this.popTransform();
				}
			}
		} else if (!this.mHidden && pLocalName.equals("ellipse")) {
			final Float centerX = SVGParser.getFloatAttribute(pAttributes, "cx");
			final Float centerY = SVGParser.getFloatAttribute(pAttributes, "cy");
			final Float radiusX = SVGParser.getFloatAttribute(pAttributes, "rx");
			final Float radiusY = SVGParser.getFloatAttribute(pAttributes, "ry");
			if (centerX != null && centerY != null && radiusX != null && radiusY != null) {
				final boolean pushed = this.pushTransform(pAttributes);
				final SVGProperties sVGProperties = new SVGProperties(pAttributes);
				this.mRect.set(centerX - radiusX, centerY - radiusY, centerX + radiusX, centerY + radiusY);
				if (this.setFill(sVGProperties)) {
					this.setLimits(centerX - radiusX, centerY - radiusY);
					this.setLimits(centerX + radiusX, centerY + radiusY);
					this.mCanvas.drawOval(this.mRect, this.mPaint);
				}
				if (this.setStroke(sVGProperties)) {
					this.mCanvas.drawOval(this.mRect, this.mPaint);
				}
				if(pushed) {
					this.popTransform();
				}
			}
		} else if (!this.mHidden && (pLocalName.equals("polygon") || pLocalName.equals("polyline"))) {
			final NumberParserResult numberParserResult = NumberParser.parseFromAttributes(pAttributes, "points");
			if (numberParserResult != null) {
				final Path p = new Path();
				final ArrayList<Float> points = numberParserResult.getNumbers();
				if (points.size() > 1) {
					final boolean pushed = this.pushTransform(pAttributes);
					final SVGProperties sVGProperties = new SVGProperties(pAttributes);
					p.moveTo(points.get(0), points.get(1));
					for (int i = 2; i < points.size(); i += 2) {
						final float x = points.get(i);
						final float y = points.get(i + 1);
						p.lineTo(x, y);
					}
					if (!pLocalName.equals("polyline")) {
						p.close();
					}
					if (this.setFill(sVGProperties)) {
						this.setLimits(p);
						this.mCanvas.drawPath(p, this.mPaint);
					}
					if (this.setStroke(sVGProperties)) {
						this.mCanvas.drawPath(p, this.mPaint);
					}
					if(pushed) {
						this.popTransform();
					}
				}
			}
		} else if (!this.mHidden && pLocalName.equals("path")) {
			final Path p = new PathParser().parse(SAXHelper.getStringAttribute(pAttributes, "d"));
			final boolean pushed = this.pushTransform(pAttributes);
			final SVGProperties sVGProperties = new SVGProperties(pAttributes);
			if (this.setFill(sVGProperties)) {
				this.setLimits(p);
				this.mCanvas.drawPath(p, this.mPaint);
			}
			if (this.setStroke(sVGProperties)) {
				this.mCanvas.drawPath(p, this.mPaint);
			}
			if(pushed) {
				this.popTransform();
			}
		} else if (!this.mHidden) {
			Debug.d("Unexpected SVG tag: '" + pLocalName +"'!");
		}
	}

	@Override
	public void characters(final char pCharacters[], final int pStart, final int pLength) {
		/* Nothing. */
	}

	@Override
	public void endElement(final String pNamespace, final String pLocalName, final String pQualifiedName)
	throws SAXException {
		if (pLocalName.equals("svg")) {
			this.mPicture.endRecording();
		} else if (pLocalName.equals("g")) {
			if (this.mBoundsMode) {
				this.mBoundsMode = false;
			}
			/* Pop group transform if there was one pushed. */
			if(this.mGroupTransformStack.pop()) {
				this.popTransform();
			}
			/* Break out of hidden mode. */
			if (this.mHidden) {
				this.mHiddenLevel--;
				if (this.mHiddenLevel == 0) {
					this.mHidden = false;
				}
			}
			/* Clear shader map. */
			this.mSVGPaint.clearGradientShaders();
		}
	}

	// ===========================================================
	// Methods
	// ===========================================================

	private boolean setFill(final SVGProperties pSVGProperties) {
		if(this.isDisplayNone(pSVGProperties) || this.isFillNone(pSVGProperties)) {
			return false;
		}

		this.resetPaint();
		this.mPaint.setStyle(Paint.Style.FILL);

		final String fillProperty = pSVGProperties.getStringProperty("fill");
		if(fillProperty == null) {
			if(pSVGProperties.getStringProperty("stroke") == null) {
				/* Default is black fill. */
				this.mPaint.setColor(0xFF000000);
				return true;
			} else {
				return false;
			}
		} else {
			return this.mSVGPaint.setColor(pSVGProperties, fillProperty);
		}
	}

	private void resetPaint() {
		this.mPaint.reset();
		this.mPaint.setAntiAlias(true);
	}

	private boolean setStroke(final SVGProperties pSVGProperties) {
		if(this.isDisplayNone(pSVGProperties) || this.isStrokeNone(pSVGProperties)) {
			return false;
		}

		this.resetPaint();
		this.mPaint.setStyle(Paint.Style.STROKE);

		final String strokeProperty = pSVGProperties.getStringProperty("stroke");
		if(this.mSVGPaint.setColor(pSVGProperties, strokeProperty)) {
			final Float width = pSVGProperties.getFloatProperty("stroke-width");
			if (width != null) {
				this.mPaint.setStrokeWidth(width);
			}
			final String linecap = pSVGProperties.getStringProperty("stroke-linecap");
			if ("round".equals(linecap)) {
				this.mPaint.setStrokeCap(Paint.Cap.ROUND);
			} else if ("square".equals(linecap)) {
				this.mPaint.setStrokeCap(Paint.Cap.SQUARE);
			} else if ("butt".equals(linecap)) {
				this.mPaint.setStrokeCap(Paint.Cap.BUTT);
			}
			final String linejoin = pSVGProperties.getStringProperty("stroke-linejoin");
			if ("miter".equals(linejoin)) {
				this.mPaint.setStrokeJoin(Paint.Join.MITER);
			} else if ("round".equals(linejoin)) {
				this.mPaint.setStrokeJoin(Paint.Join.ROUND);
			} else if ("bevel".equals(linejoin)) {
				this.mPaint.setStrokeJoin(Paint.Join.BEVEL);
			}
			return true;
		} else {
			return false;
		}
	}

	private void setLimits(final float pX, final float pY) {
		if (pX < this.mLimits.left) {
			this.mLimits.left = pX;
		}
		if (pX > this.mLimits.right) {
			this.mLimits.right = pX;
		}
		if (pY < this.mLimits.top) {
			this.mLimits.top = pY;
		}
		if (pY > this.mLimits.bottom) {
			this.mLimits.bottom = pY;
		}
	}

	private void setLimits(final float pX, final float pY, final float pWidth, final float pHeight) {
		this.setLimits(pX, pY);
		this.setLimits(pX + pWidth, pY + pHeight);
	}

	private void setLimits(final Path pPath) {
		pPath.computeBounds(this.mRect, false);
		this.setLimits(this.mRect.left, this.mRect.top);
		this.setLimits(this.mRect.right, this.mRect.bottom);
	}

	private boolean pushTransform(final Attributes pAttributes) {
		final String transform = SAXHelper.getStringAttribute(pAttributes, "transform");
		if(transform == null) {
			return false;
		} else {
			final Matrix matrix = TransformParser.parseTransform(transform);
			this.mCanvas.save();
			this.mCanvas.concat(matrix);
			return true;
		}
	}

	private void popTransform() {
		this.mCanvas.restore();
	}

	private boolean isDisplayNone(final SVGProperties pSVGProperties) {
		return "none".equals(pSVGProperties.getStringProperty("display"));
	}

	private boolean isFillNone(final SVGProperties pSVGProperties) {
		return "none".equals(pSVGProperties.getStringProperty("fill"));
	}

	private boolean isStrokeNone(final SVGProperties pSVGProperties) {
		return "none".equals(pSVGProperties.getStringProperty("stroke"));
	}

	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================
}