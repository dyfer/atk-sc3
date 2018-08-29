/*
	Copyright the ATK Community and Joseph Anderson, 2011-2017
        J Anderson	j.anderson[at]ambisonictoolkit.net
        M McCrea    mtm5[at]uw.edu

	This file is part of SuperCollider3 version of the Ambisonic Toolkit (ATK).

	The SuperCollider3 version of the Ambisonic Toolkit (ATK) is free software:
	you can redistribute it and/or modify it under the terms of the GNU General
	Public License as published by the Free Software Foundation, either version 3
	of the License, or (at your option) any later version.

	The SuperCollider3 version of the Ambisonic Toolkit (ATK) is distributed in
	the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
	implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See
	the GNU General Public License for more details.

	You should have received a copy of the GNU General Public License along with the
	SuperCollider3 version of the Ambisonic Toolkit (ATK). If not, see
	<http://www.gnu.org/licenses/>.
*/


//---------------------------------------------------------------------
//	The Ambisonic Toolkit (ATK) is a soundfield kernel support library.
//
// 	Class: HoaMatrixEncoder
// 	Class: HoaMatrixDecoder
// 	Class: HoaMatrixXformer
//
//	The Ambisonic Toolkit (ATK) is intended to bring together a number of tools and
//	methods for working with Ambisonic surround sound. The intention is for the toolset
//	to be both ergonomic and comprehensive, providing both classic and novel algorithms
//	to creatively manipulate and synthesise complex Ambisonic soundfields.
//
//	The tools are framed for the user to think in terms of the soundfield kernel. By
//	this, it is meant the ATK addresses the holistic problem of creatively controlling a
//	complete soundfield, allowing and encouraging the composer to think beyond the placement
//	of sounds in a sound-space and instead attend to the impression and image of a soundfield.
//	This approach takes advantage of the model the Ambisonic technology presents, and is
//	viewed to be the idiomatic mode for working with the Ambisonic technique.
//
//
//	We hope you enjoy the ATK!
//
//	For more information visit http://ambisonictoolkit.net/ or
//	email info[at]ambisonictoolkit.net
//
//---------------------------------------------------------------------

HoaMatrix : AtkMatrix {

	var <>directions;  // setter added for matrix-to-file & file-to-matrix support

	// call by subclass, only
	*newFromMatrix { |matrix, directions = ([[ 0, 0 ]]), order = (Hoa.defaultOrder)|
		^super.new('fromMatrix', order).initDirections(directions).initFromMatrix(matrix)
	}

	initFromMatrix { |aMatrix|
		var numCoeffs, matrixOrder;

		// set instance matrix
		matrix = aMatrix;

		// 1) Check: matrix numChannels == directions.size
		(this.numChannels != this.directions.size).if({
			Error(
				format(
					"[%:-initFromMatrix] Matrix number of channels "
					"should matches directions. Number of channels = %, number of directions = %",
					this.class.asString, this.numChannels, this.directions.size
				)
			).errorString.postln;
			this.halt;
		});

		// 2) Check: matrix numCoeffs == declared order
		numCoeffs = switch (this.type,
			'encoder', {matrix.rows},
			'decoder', {matrix.cols},
			'xformer', {matrix.rows},
		);
		matrixOrder = Hoa.detectOrder(numCoeffs);

		(this.order != matrixOrder).if({
			Error(
				format(
					"[%:-initFromMatrix] Order specified doesn't match order "
					"detected from the size of the matrix. (specified: %, detected: %)",
					this.class.asString, this.order, matrixOrder
				)
			).errorString.postln;
			this.halt;
		});

		// 3) Check: xformer is square matrix
		((this.type == 'xformer') && (matrix.isSquare.not)).if({
			Error(
				format(
					"[%:-initFromMatrix] An 'xformer' matrix "
					"should be square. rows = %, cols = %",
					this.class.asString, matrix.rows, matrix.cols
				)
			).errorString.postln;
			this.halt;
		})
	}

	initDirections { |argDirections|
		directions = (argDirections == nil).if({
			Hoa.numOrderCoeffs(this.order).collect({ inf })
		}, {
			argDirections.rank.switch(
				0, { Array.with(argDirections, 0.0).reshape(1, 2) },
				1, { argDirections.collect({ |dir| Array.with(dir, 0.0)}) },
				2, { argDirections },
			).collect({ |thetaPhi|  // wrap to [ +/-pi, +/-pi/2 ]
				Spherical.new(1, thetaPhi.at(0), thetaPhi.at(1)).asCartesian.asSpherical.angles
			})
		})
	}

	initDirTDesign { |design, order|
		var minT = 2 * order;

		// check for valid t
		(design.t >= minT).if({
			directions = design.directions
		}, {
			format(
				"[HoaMatrix -initDirTDesign] A t-design of t >= % is required for order %.\nSupplied design t: ",
				minT, order, design.t
			).throw
		})
	}

	initFormat { |inputFormat = \atk, outputFormat = \atk|
		var hoaOrder;
		var size;
		var coeffs;
		var colIndices, rowIndices;
		var formatKeyword;

		formatKeyword = { |format|
			(format.class == Symbol).if({
				Hoa.formatDict[format]  // retreive named formats
			}, {
				format  // otherwise, presume valid array
			})
		};

		// test for single keyword format
		inputFormat = formatKeyword.value(inputFormat);
		outputFormat = formatKeyword.value(outputFormat);

		hoaOrder = this.order.asHoaOrder;  // instance order
		size = hoaOrder.numCoeffs;

		(inputFormat == outputFormat).if({  // equal formats?
			matrix = Matrix.newIdentity(size).asFloat
		}, {  // unequal formats

			// 1) normalization - returned coefficients are ordered \acn
			coeffs = (inputFormat.at(1) == outputFormat.at(1)).if({
				Array.fill(size, { 1.0 })
			}, {
				hoaOrder.normalisation(outputFormat.at(1)) / hoaOrder.normalisation(inputFormat.at(1))
			});

			// 2) generate matrix
			colIndices = hoaOrder.indices(inputFormat.at(0));
			rowIndices = hoaOrder.indices(outputFormat.at(0));
			matrix = Matrix.newClear(size, size).asFloat;
			size.do({ |index|  // index increment ordered \acn
				matrix.put(
					rowIndices.at(index),
					colIndices.at(index),
					coeffs.at(index)
				)
			})
		})
	}

	// overrides AtkMatrix:loadFromLib - order required to resolve set path
	loadFromLib { |order ...args|
		var pathStr;
		pathStr = this.kind.asString ++ "/";

		if (args.size==0) {
			// no args... filename is assumed to be this.kind
			pathStr = this.kind.asString;
		} {
			args.do{ |argParam, i|
				pathStr = if (i > 0) {
					format("%-%", pathStr, argParam.asString)
				} {
					format("%%", pathStr, argParam.asString)
				};
			};
		};

		this.initFromFile(pathStr++".yml", this.type, order, false);

		switch( this.type,
			'\encoder', {this.initEncoderVarsForFiles}, // properly set dirInputs
			'\decoder', {this.initDecoderVarsForFiles}, // properly set dirOutputs
			'\xformer', {}
		)
	}


	// TODO: these utilities could move to AtkMatrix, or elsewhere?

	getSub { |rowStart=0, colStart=0, rowLength, colLength|
		^this.matrix.getSub(rowStart, colStart, rowLength, colLength)
	}

	postSub { |rowStart=0, colStart=0, rowLength, colLength, round=0.001|
		this.matrix.postSub(rowStart, colStart, rowLength, colLength, round)
	}

	// get the block matrix for a single degree within the full matrix
	getDegreeBlock { |degree, round = 0.001|
		var st = Hoa.degreeStIdx(degree);
		var nCoeffs = Hoa.numDegreeCoeffs(degree);

		^this.matrix.getSub(st, st, nCoeffs, nCoeffs, round);
	}

	// post the block matrix for a single degree within the full matrix
	postDegreeBlock { |degree, round = 0.001|
		var st = Hoa.degreeStIdx(degree);
		var nCoeffs = Hoa.numDegreeCoeffs(degree);

		this.matrix.postSub(st, st, nCoeffs, nCoeffs, round);
	}

	// force values to zero that are within threshold distance (positive or negative)
	zeroWithin { |within = (Hoa.nearZero)|
		this.matrix.zeroWithin(within);
	}

	// mix coefficients with a transform matrix
	mixCoeffs { |coeffs|

		if (coeffs.size != this.matrix.cols) {
			format("AtkMatrix:mixCoeffs - coeffs.size [%] != matrix.cols [%]", coeffs.size, this.matrix.cols).throw
		};

		// NOTE: .asList added to force Collection:flop.
		// Array:flop uses a primitive that has a GC bug:
		// https://github.com/supercollider/supercollider/issues/3454
		^this.matrix.cols.collect({|i|
			this.matrix.getCol(i) * coeffs[i]
		}).asList.flop.collect{|me| me.sum};
	}

	// ---------
	// return info

	// infer from subclass and order
	set { ^(this.class.asString.keep(3).toUpper ++ this.order.asString).asSymbol }

	// infer from subclass
	type { ^this.class.asString.keep(-7).toLower.asSymbol }

	numChannels {
		^switch( this.type,
			'\encoder', { this.numInputs },
			'\decoder', { this.numOutputs },
			'\xformer', { Hoa.numOrderCoeffs(this.order) }
		)
	}

	dim {
		var is2D;

		^if (this.kind == \format, {
			3
		}, {
			// catch unspecified directions, e.g. if *newFromMatrix
			if (this.directions[0] == 'unspecified') {
				// TODO: consider how returning a Symbol will affect other calls on -dim
				'unspecified'
			} {
				is2D = this.directions.collect(_.last).every(_ == 0.0);
				if (is2D, { 2 }, { 3 });
			}
		})
	}

	dirInputs {
		^switch( this.type,
			'\encoder', { this.directions },
			'\decoder', { this.numInputs.collect({ inf }) },
			// '\xformer', { this.numInputs.collect({ inf }) },
			'\xformer', { this.directions }  // requires set to inf
		)
	}

	dirOutputs {
		^switch( this.type,
			'\encoder', { this.numOutputs.collect({ inf }) },
			'\decoder', { this.directions },
			// '\xformer', { this.numInputs.collect({ inf }) },
			'\xformer', { this.directions }  // requires set to inf
		)
	}

}

//-----------------------------------------------------------------------
// matrix encoders

HoaMatrixEncoder : HoaMatrix {

	// Format Encoder
	*newFormat { |format =\atk, order = (Hoa.defaultOrder)|
		^super.new('format', order).initDirections.initFormat(format, \atk);
	}

	// Projection Encoding beam - 'basic' & multi pattern
	*newDirection { |theta = 0, phi = 0, beamShape = nil, order = (Hoa.defaultOrder)|
		var directions = [[ theta, phi ]];
		var instance = super.new('dir', order).initDirections(directions);

		^(beamShape == nil).if({
			instance.initBasic  // (\basic, \amp)
		}, {
			instance.initBeam(beamShape, nil)  // (beamShape, \amp)
		})
	}

	// Projection Encoding beams - 'basic' & multi pattern
	*newDirections { |directions = ([[ 0, 0 ]]), beamShape = nil, match = nil, order = (Hoa.defaultOrder)|
		var instance = super.new('dirs', order).initDirections(directions);
		^case
		{ (beamShape == nil) && (match == nil) } { instance.initBasic }  // (\basic, \amp)
		{ (beamShape != nil) && (match == nil) } { instance.initBeam(beamShape, \beam) }
		{ (beamShape == nil) && (match != nil) } { instance.initBeam(\basic, match) }
		{ (beamShape != nil) && (match != nil) } { instance.initBeam(beamShape, match) };
	}

	// Projection Encoding beams (convenience to match FOA: may wish to deprecate) - 'basic' pattern
    *newPanto { |numChans = 4, orientation = \flat, order = (Hoa.defaultOrder)|
		var directions = Array.regularPolygon(numChans, orientation, pi);
		^super.new('panto', order).initDirections(directions).initBasic;
    }

	// Modal Encoding beams - multi pattern
	*newModeMatch { |directions = ([[ 0, 0 ]]), beamShape = \basic, match = \beam, order = (Hoa.defaultOrder)|
        ^super.new('modeMatch', order).initDirections(directions).initMode(beamShape, match);
    }

	// spherical design wrapper for *newBeams, match = \beam
	*newSphericalDesign { |design, beamShape = \basic, order = (Hoa.defaultOrder)|
		var instance = super.new('spherical', order);

		^switch
		( design.class,
			TDesign, { instance.initDirTDesign(design, order).initBeam(beamShape, \beam) },  // TDesign only, for now
			{ format(  // ... or, catch un-supported
				"[HoaMatrixEncoder *newSphericalDesign] Design % is not supported!",
				design.class
			).throw
			}
		)
	}

	*newFromFile { arg filePathOrName, order = (Hoa.defaultOrder);
        ^super.new.initFromFile(filePathOrName, 'encoder', order, true).initEncoderVarsForFiles
    }


    // ------------
    // Basic

    initBasic {  // basic beam encoder, beamShape = \basic, match = \amp
		var hoaOrder;

        hoaOrder = this.order.asHoaOrder;  // instance order

        // build encoder matrix, and set for instance
		// norm = 1.0, beamWeights = [ 1, 1, ..., 1 ]
        matrix = Matrix.with(
			this.directions.collect({ |thetaPhi|
                hoaOrder.sph(thetaPhi.at(0), thetaPhi.at(1))
			}).flop
        ).zeroWithin(Hoa.nearZero)
    }

    // ------------
	// Multi-pattern (projection)

    initBeam {  |beamShape, match| // beam encoder
        var hoaOrder, beamWeights;
		var degreeSeries, norm;

        hoaOrder = this.order.asHoaOrder;  // instance order
        beamWeights = hoaOrder.beamWeights(beamShape);

		degreeSeries = Array.series(this.order+1, 1, 2);
		norm = (degreeSeries * beamWeights).sum / degreeSeries.sum;
		// rescale for matching a/b normalization?
		(match == \beam).if({
			norm = degreeSeries.sum / this.directions.size * norm
		});

        // build encoder matrix, and set for instance
        matrix = norm * Matrix.with(
            this.directions.collect({ |thetaPhi|
				(1/beamWeights)[hoaOrder.l] * hoaOrder.sph(thetaPhi.at(0), thetaPhi.at(1));
            }).flop
        ).zeroWithin(Hoa.nearZero)
    }

    // ------------
	// Multi-pattern (modal)

    initMode {  |beamShape, match| // modal encoder
		var decodingMatrix;

		// build decoder matrix
		decodingMatrix = HoaMatrixDecoder.newDirections(
			this.directions,
			beamShape,
			match,
			this.order
		).matrix;

		// match modes
		matrix = decodingMatrix.pseudoInverse.zeroWithin(Hoa.nearZero)
	}

	initEncoderVarsForFiles {
		directions = if (fileParse.notNil) {
			if (fileParse.dirInputs.notNil) {
				fileParse.dirInputs.asFloat
			} {
				matrix.cols.collect({'unspecified'})
			};
		} { // txt file provided, no fileParse
			matrix.cols.collect({'unspecified'});
		};
	}
}


//-----------------------------------------------------------------------
// matrix transforms


HoaMatrixXformer : HoaMatrix {

    // ------------
    // From matrix

	// overload HoaMatrix *newFromMatrix
	*newFromMatrix { |matrix, order = (Hoa.defaultOrder)|
		^super.new('fromMatrix', order).initDirections(nil).initFromMatrix(matrix)
	}

    // ------------
    // Rotation

	*newRotate { |r1 = 0, r2 = 0, r3 = 0, axes = \xyz, order = (Hoa.defaultOrder)|
		^super.new('rotate', order).initDirections.initRotation(r1, r2, r3, axes, order)
	}

	*newRotateAxis { |axis = \z, angle = 0, order = (Hoa.defaultOrder)|
		var r1=0,r2=0,r3=0;
		switch( axis,
			\x, {r1=angle},
			\y, {r2=angle},
			\z, {r3=angle},
			\rotate, {r3=angle},
			\tilt, {r1=angle},
			\tumble, {r2=angle},
			\yaw, {r3=angle},
			\pitch, {r2=angle},
			\roll, {r1=angle},
		);
		^super.new('rotateAxis', order).initDirections.initRotation(r1, r2, r3, \xyz)
	}

	*newRTT { |rotate = 0, tilt = 0, tumble = 0, order = (Hoa.defaultOrder)|
		^super.new('rotation', order).initDirections.initRotation(rotate, tilt, tumble, \zxy)
	}

	*newYPR { |yaw = 0, pitch = 0, roll = 0, order = (Hoa.defaultOrder)|
		^super.new('rotation', order).initDirections.initRotation(roll, pitch, yaw, \xyz)
	}

	/// ------------
    // Mirroring

	*newReflect { |mirror = \reflect, order = (Hoa.defaultOrder)|
		^super.new('reflect', order).initDirections.initReflect(mirror);
	}

	// Swap one axis for another.
	// TODO: This a subset of mirroring. Wrap into *newReflect method?
	// - if yes, would need a way to fork to initSwapAxes in *newReflect
	// - if yes, kind = 'mirror', otherwise need new kind e.g. 'axisSwap'
	*newSwapAxes { |axes = \yz, order = (Hoa.defaultOrder)|
		^super.new('swap', order).initDirections.initSwapAxes(axes);
	}

    // ------------
    // Beaming & nulling

    *newBeam { |theta = 0, phi = 0, beamShape = \basic, order = (Hoa.defaultOrder)|
		var directions = [[ theta, phi ]];
        ^super.new('beam', order).initDirections(directions).initBeam(beamShape);
    }

    *newNull { |theta = 0, phi = 0, beamShape = \basic, order = (Hoa.defaultOrder)|
		var directions = [[theta, phi]];
        ^super.new('null', order).initDirections(directions).initNull(beamShape);
    }

	initRotation { |r1, r2, r3, convention|
		matrix = HoaRotationMatrix(r1, r2, r3, convention, this.order).matrix.zeroWithin(Hoa.nearZero);
	}

    initReflect { |mirror|
        var hoaOrder;
        var coeffs;

        hoaOrder = this.order.asHoaOrder;  // instance order

        // 1) generate mirror coefficients - ordered \acn
        coeffs = hoaOrder.reflection(mirror);

        // 2) generate matrix
        matrix = Matrix.newDiagonal(coeffs);
    }

	// NOTE: this contains near-zero values.
	// You can optimize these out by calling Matrix:-zeroWithin
	initSwapAxes { |axes|
		case
		{ axes == \yz or: { axes == \zy } } // swap Z<>Y axes, "J-matrix"
		{
			var rx, my;

			rx = this.class.newRotateAxis(\x, 0.5pi, this.order).matrix;
			my = this.class.newReflect(\y, this.order).matrix;

			// matrix = my * rx;

			// TODO: bake in this omptimization? larger question of migrating from Matrix to MatrixArray
			rx = MatrixArray.with(rx.asArray);
			my = MatrixArray.with(my.asArray);
			matrix = Matrix.with(my * rx);
		}
		{ axes == \xz or: { axes == \zx } } // swap Z<>X axes, "K-matrix"
		{
			var ry, mx;

			ry = this.class.newRotateAxis(\y, 0.5pi, this.order).matrix;
			mx = this.class.newReflect(\x, this.order).matrix;

			// matrix = mx * ry;

			// TODO: bake in this omptimization? larger question of migrating from Matrix to MatrixArray
			mx = MatrixArray.with(mx.asArray);
			ry = MatrixArray.with(ry.asArray);
			matrix = Matrix.with(mx * ry);
		}
		{ axes == \xy or: { axes == \yx } } // swap X<>Y axes
		{
			var rz, mx;

			rz = this.class.newRotateAxis(\z, 0.5pi, this.order).matrix;
			mx = this.class.newReflect(\x, this.order).matrix;

			matrix = mx * rz;
		}
		{
			"Cannot swap axes '%'".format(axes).throw
		};
	}

	initBeam { |beamShape|
		var theta, phi;
		var decodingMatrix, encodingMatrix;

		#theta, phi = this.directions.at(0);

		// build decoder matrix
		decodingMatrix = HoaMatrixDecoder.newDirection(
			theta,
			phi,
			beamShape,
			this.order
		).matrix;

		// build encoder matrix
		encodingMatrix = HoaMatrixEncoder.newDirection(
			theta,
			phi,
			\basic,
			this.order
		).matrix;

		// decode, re-encode
		matrix = encodingMatrix.mulMatrix(decodingMatrix)
	}

	initNull { |beamShape|
		var theta, phi;
		var decodingMatrix, encodingMatrix;
		var xformingMatrix;

		#theta, phi = this.directions.at(0);

		// build xforming matrix
		xformingMatrix = HoaMatrixXformer.newBeam(
			theta,
			phi,
			beamShape,
			this.order
		).matrix;

		// null
		matrix = Matrix.newIdentity((this.order+1).squared) - xformingMatrix
	}

	// *newFromFile { arg filePathOrName;
	// 	^super.new.initFromFile(filePathOrName, 'xformer', true);
	// }

	// ------------
	// Analysis

	analyzeDirections { |testDirections|
		var encDirs, xyzEncDirs;
		var encodingMatrix;
		var b, b2, amp, energy, rms;
		var designDict, design;
		var g, g2, e;
		var rVxyz, rVsphr, rVmag, rVdir, rVu;
		var rExyz, rEsphr, rEmag, rEdir, rEu;
		var spreadE;
		var rVdist, rEdist, rVrEdist;

		// reshape (test) encoding directions, as need be...
		// ... then find test unit vectors
		xyzEncDirs = testDirections.rank.switch(
			0, { Array.with(testDirections, 0.0).reshape(1, 2) },
			1, { testDirections.collect({ |dir| Array.with(dir, 0.0)}) },
			2, { testDirections },
		).collect({ |thetaPhi|
			Spherical.new(1, thetaPhi.at(0), thetaPhi.at(1)).asCartesian.asArray
		});
		// ... and back to [ theta, phi ]...
		encDirs = xyzEncDirs.collect({ |xyz|  // wrap to [ +/-pi, +/-pi/2 ]
			Cartesian.new(xyz.at(0), xyz.at(1), xyz.at(2), ).asSpherical.angles
		});

		// encode test directions (basic), return encoding matrix
		encodingMatrix = HoaMatrixEncoder.newDirections(
			encDirs,
			order: this.order
		).matrix;

		// encode, to return resulting spherical coefficients
		// for each (test) encoding direction
		b = this.matrix.mulMatrix(encodingMatrix);

		// energy, expected matrix is Real only,
		// use -squared rather than -abs.squared
		b2 = b.squared;

		// pressure for each (test) encoding direction
		amp = b.getRow(0);

		// energy for each (test) encoding direction
		energy = b2.sumCols / (this.order + 1).squared;

		// rms for each (test) encoding direction
		rms = energy;

		// ------------
		// rV

		// rV (Active Admittance) can be directly found from
		// the Real degree 0 & 1 SN3D spherical coefficients
		// for each (test) encoding direction
		rVxyz = HoaMatrixDecoder.newFormat(
			[\acn, \sn3d],
			this.order
		).matrix.mulMatrix(b).flop.asArray.collect({ |bSn3d|
			(bSn3d[HoaDegree.new(1).indices] / bSn3d[HoaDegree.new(0).indices]).rotate(1)
		});
		rVxyz = Matrix.with(rVxyz).zeroWithin(Hoa.nearZero).asArray;

		// in spherical, for convenience to find rVmag, rVdir
		rVsphr = rVxyz.collect({ |xyz|
			var x, y, z;
			#x, y, z = xyz;
			Cartesian.new(x, y, z).asSpherical
		});
		rVmag = rVsphr.collect({ |sphr| sphr.rho });
		rVdir = rVsphr.collect({ |sphr| sphr.angles });

		// normalize rVxyz to unit vector
		rVu = rVxyz / rVmag;

		// ------------
		// rE

		// decode transformed test directions to energy optimized sphere,
		// to return resulting virtual loudspeaker gains (amps)
		// for each (test) encoding direction
		designDict = TDesignLib.getHoaDesigns(\spreadE, this.order).first;
		design = TDesign.new(designDict[\numPoints], designDict[\t], designDict[\dim]);

		g = HoaMatrixDecoder.newSphericalDesign(
			design,
			\energy,
			this.order
		).matrix.mulMatrix(b);

		// energy, expected matrix is Real only,
		// use -squared rather than -abs.squared
		g2 = g.squared;

		// re-encode from energy optimized sphere
		e = HoaMatrixEncoder.newSphericalDesign(
			design,
			\basic,
			this.order
		).matrix.mulMatrix(g2);

		// rE (as energy optimized Active Admittance) can be directly found from
		// the Real degree 0 & 1 SN3D spherical coefficients
		// for each (test) encoding direction
		rExyz = HoaMatrixDecoder.newFormat(
			[\acn, \sn3d],
			this.order
		).matrix.mulMatrix(e).flop.asArray.collect({ |bSn3d|
			(bSn3d[HoaDegree.new(1).indices] / bSn3d[HoaDegree.new(0).indices]).rotate(1)
		});
		rExyz = Matrix.with(rExyz).zeroWithin(Hoa.nearZero).asArray;

		// in spherical, for convenience to find rEmag, rEdir
		rEsphr = rExyz.collect({ |xyz|
			var x, y, z;
			#x, y, z = xyz;
			Cartesian.new(x, y, z).asSpherical
		});
		rEmag = rEsphr.collect({ |sphr| sphr.rho });
		rEdir = rEsphr.collect({ |sphr| sphr.angles });

		// normalize rExyz to unit vector
		rEu = rExyz / rEmag;

		// find 1/2 angle energy spread
		spreadE = Dictionary.with(*[
			\cos->rEmag.acos,  // Zotter & Frank: ~-3dB
			\hvc->((2 * rEmag) - 1).acos  // Carpentier, Politis: ~-6dB
		]);

		// ------------
		// measure rV & rE direction distortion

		// (test) encoding directions vs rV
		rVdist = (xyzEncDirs * rVu).collect({ |item|  	// arccos(dot product)
			item.sum.acos
		});
		// (test) encoding directions vs rE
		rEdist = (xyzEncDirs * rEu).collect({ |item|  	// arccos(dot product)
			item.sum.acos
		});
		// rV vs rE
		rVrEdist = (rVu * rEu).collect({ |item|  	// arccos(dot product)
			item.sum.acos
		});

		// return
		^Dictionary.with(*[
			\amp->amp,
			\rms->rms,
			\energy->energy,
			\spreadE->spreadE,
			\rV->Dictionary.with(*[
				\xyz->rVxyz, \mag->rVmag, \directions->rVdir,
				\dist->rVdist, \rEdist->rVrEdist
			]),
			\rE->Dictionary.with(*[
				\xyz->rExyz, \mag->rEmag, \directions->rEdir,
				\dist->rEdist, \rVdist->rVrEdist
			]),
		])
	}

	analyzeAverage {
		var amp, energy, rms;
		var meanE;

		// average pressure
		amp = this.matrix.get(0, 0);

		// average energy
		energy = this.matrix.squared.sum / this.numChannels;

		// average rms: numChannels = numCoeffs
		rms = energy;

		// meanE
		meanE = this.numChannels * energy / amp.squared;

		// return
		^Dictionary.with(*[
			\amp->amp,
			\rms->rms,
			\energy->energy,
			\meanE->meanE,
			\matchWeight->Dictionary.with(*[
				\amp->amp.reciprocal,
				\rms->rms.sqrt.reciprocal,
				\energy->energy.sqrt.reciprocal,
			])
		])
	}

    dim { ^3 }  // all transforms are 3D

}


//-----------------------------------------------------------------------
// matrix decoders


HoaMatrixDecoder : HoaMatrix {

	// Format Encoder
	*newFormat { |format = \atk, order = (Hoa.defaultOrder)|
		^super.new('format', order).initDirections.initFormat(\atk, format);
	}

	// Projection Decoding beam - 'basic' & multi pattern
	*newDirection { |theta = 0, phi = 0, beamShape, order = (Hoa.defaultOrder)|
		var directions = [[ theta, phi ]];
		var instance = super.new('dir', order).initDirections(directions);

		^(beamShape == nil).if({
			instance.initBasic  // (\basic, \beam)
		}, {
			instance.initBeam(beamShape, nil)  // (beamShape, \beam)
		})
	}

	// Projection Decoding beams - 'basic' & multi pattern
	*newDirections { |directions = ([[ 0, 0 ]]), beamShape = nil, match = nil, order = (Hoa.defaultOrder)|
		var instance = super.new('dirs', order).initDirections(directions);
		^case
		{ (beamShape == nil) && (match == nil) } { instance.initBeam(\basic, \amp) }
		{ (beamShape != nil) && (match == nil) } { instance.initBeam(beamShape, \beam) }
		{ (beamShape == \basic) && (match == \beam) } { instance.initBasic }  // (\basic, \beam)
		{ (beamShape == nil) && (match != nil) } { instance.initBeam(\basic, match) }
		{ (beamShape != nil) && (match != nil) } { instance.initBeam(beamShape, match) };
	}

	// Projection: Simple Ambisonic Decoding, aka SAD
    *newProjection { |directions, beamShape = \basic, match = \amp, order = (Hoa.defaultOrder)|
		^super.new('projection', order).initDirections(directions).initSAD(beamShape, match);
    }

	// Projection: Simple Ambisonic Decoding, aka SAD (convenience to match FOA: may wish to deprecate)
	*newPanto { |numChans = 4, orientation = \flat, beamShape = \basic, match = \amp, order = (Hoa.defaultOrder)|
		var directions = Array.regularPolygon(numChans, orientation, pi);
		^super.new('panto', order).initDirections(directions).initSAD(beamShape, match);
	}

	// Mode Match: Mode Matched Decoding, aka Pseudoinverse
    *newModeMatch { |directions, beamShape = \basic, match = \amp, order = (Hoa.defaultOrder)|
		^super.new('modeMatch', order).initDirections(directions).initMMD(beamShape, match)
    }

	// Diametric: Mode Matched Decoding, aka Diametric Pseudoinverse
	*newDiametric { |directions, beamShape = \basic, match = \amp, order = (Hoa.defaultOrder)|
		var directionPairs = directions ++ directions.rank.switch(
			1, {  // 2D
				directions.collect({ |item|
					Polar.new(1, item).neg.angle
				})
			},
			2, {  // 3D
				directions.collect({ |item|
					Spherical.new(1, item.at(0), item.at(1)).neg.angles
				})
			}
		);
		^super.new('diametric', order).initDirections(directionPairs).initMMD(beamShape, match)
	}

	// spherical design wrapper for *newBeams, match = \beam
	*newSphericalDesign { |design, beamShape = \basic, order = (Hoa.defaultOrder)|
		var instance = super.new('spherical', order);

		^switch
		( design.class,
			TDesign, { instance.initDirTDesign(design, order).initBeam(beamShape, \beam) },  // TDesign only, for now
			{ format(  // ... or, catch un-supported
				"[HoaMatrixEncoder *newSphericalDesign] Design % is not supported!",
				design.class
			).throw
			}
		)
	}

    // *newFromFile { arg filePathOrName;
    //     ^super.new.initFromFile(filePathOrName, 'decoder', true).initDecoderVarsForFiles;
    // }

    // ------------
    // Basic

	initBasic {  // basic beam decoder, beamShape = \basic, match = \beam
        var hoaOrder;
		var degreeSeries, norm;

        hoaOrder = this.order.asHoaOrder;  // instance order

		degreeSeries = Array.series(this.order+1, 1, 2);
		norm = 1 / degreeSeries.sum;

        // build decoder matrix, and set for instance
		// beamWeights = [ 1, 1, ..., 1 ]
        matrix =  norm * Matrix.with(
            this.directions.collect({ |thetaPhi|
               hoaOrder.sph(thetaPhi.at(0), thetaPhi.at(1))
            })
        ).zeroWithin(Hoa.nearZero)
    }

    // ------------
	// Multi-pattern (projection)

	initBeam {  |beamShape, match| // beam decoder
		var hoaOrder, beamWeights;
		var degreeSeries, norm;

		hoaOrder = this.order.asHoaOrder;  // instance order
		beamWeights = hoaOrder.beamWeights(beamShape);

		degreeSeries = Array.series(this.order+1, 1, 2);
		norm = 1 / (degreeSeries * beamWeights).sum;
		// rescale for matching a/b normalization?
		(match == \amp).if({
			norm = degreeSeries.sum / this.directions.size * norm
		});

		// build decoder matrix, and set for instance
		matrix = norm * Matrix.with(
			this.directions.collect({ |thetaPhi|
				beamWeights[hoaOrder.l] * hoaOrder.sph(thetaPhi.at(0), thetaPhi.at(1));
			})
		).zeroWithin(Hoa.nearZero)
	}

    // ------------
	// Projection: Simple Ambisonic Decoding, aka SAD

	initSAD {  |beamShape, match| // sampling beam decoder, with matching gain
		var numOutputs;
		var inputOrder, outputOrder, hoaOrder;
		var encodingMatrix, decodingMatrix;
		var weights;
		var dim;

		// init
		inputOrder = this.order;
		numOutputs = this.directions.size;
		dim = this.dim;

		// 1) determine decoder output order - estimate
		outputOrder = dim.switch(
			2, {  // 2D
				(numOutputs >= (2 * inputOrder + 1)).if({
					inputOrder
				}, {
					((numOutputs - 1) / 2).asInteger
				})
			},
			3, {  // 3D
				(numOutputs >= (inputOrder + 1).squared).if({
					inputOrder
				}, {
					numOutputs.sqrt.asInteger - 1
				})
			}
		);
		hoaOrder = outputOrder.asHoaOrder;

		// 2) calculate weights: matching weight, beam weights
		weights = hoaOrder.matchWeight(beamShape, dim, match, numOutputs) * hoaOrder.beamWeights(beamShape, dim);
		weights = weights[hoaOrder.l];  // expand from degree...
		weights = Matrix.newDiagonal(weights);  // ... and assign to diagonal matrix

		// --------------------------------
		// 3) generate prototype planewave (basic) encoding matrix
		encodingMatrix = Matrix.with(
			this.directions.collect({ |item|
				hoaOrder.sph(item.at(0), item.at(1));  // encoding coefficients
			}).flop
		);

		// 3a) if 2D, convert (decode) output to N2D scaling & zero non-sectoral (3D) harmonics
		(dim == 2).if({
			encodingMatrix = this.class.newFormat([\acn, \n2d], outputOrder).matrix.mulMatrix(
				encodingMatrix
			);
			hoaOrder.indices.difference(hoaOrder.indices(subset: \sectoral)).do({ |row|
				encodingMatrix.putRow(row, Array.fill(numOutputs, { 0.0 }))
			})
		});

		// 4) transpose and scale: projection (basic beam, \amp norm)
		decodingMatrix = numOutputs.reciprocal * encodingMatrix.flop;

		// 4a) if 2D, convert (encode) input to N2D scaling
		// NOTE: could be included in step 3a, above
		(dim == 2).if({
			decodingMatrix = decodingMatrix.mulMatrix(
				this.class.newFormat([\acn, \n2d], outputOrder).matrix
			)
		});

		// 5) apply weights: matching weight, beam weights
		decodingMatrix = decodingMatrix.mulMatrix(weights);

		// 6) expand to match input order (if necessary)
		(inputOrder > outputOrder).if({
			decodingMatrix = (decodingMatrix.flop ++ Matrix.newClear(
				(inputOrder + 1).squared - (outputOrder + 1).squared, numOutputs)
			).flop
		});

		// assign
		matrix = decodingMatrix.zeroWithin(Hoa.nearZero)
	}

    // ------------
	// Mode Match: Mode Matched Decoding, aka Pseudoinverse

	initMMD {  |beamShape, match|  // mode matching decoder, with matching gain
		var numOutputs;
		var inputOrder, outputOrder, hoaOrder;
		var encodingMatrix, decodingMatrix, zerosMatrix;
		var weights;
		var dim;

		// init
		inputOrder = this.order;
		numOutputs = this.directions.size;
		dim = this.dim;

		// 1) determine decoder output order - estimate
		outputOrder = dim.switch(
			2, {  // 2D
				(numOutputs >= (2 * inputOrder + 1)).if({
					inputOrder
				}, {
					((numOutputs - 1) / 2).asInteger
				})
			},
			3, {  // 3D
				(numOutputs >= (inputOrder + 1).squared).if({
					inputOrder
				}, {
					numOutputs.sqrt.asInteger - 1
				})
			}
		);
		hoaOrder = outputOrder.asHoaOrder;

		// 2) calculate weights: matching weight, beam weights
		weights = hoaOrder.matchWeight(beamShape, dim, match, numOutputs) * hoaOrder.beamWeights(beamShape, dim);
		weights = weights[hoaOrder.l];  // expand from degree...
		weights = Matrix.newDiagonal(weights);  // ... and assign to diagonal matrix

		// --------------------------------
		// 3) generate prototype planewave (basic) encoding matrix
		encodingMatrix = Matrix.with(
			this.directions.collect({ |item|
				hoaOrder.sph(item.at(0), item.at(1));  // encoding coefficients
			}).flop
		);

		// 3a) if 2D, discard non-sectoral (3D) harmonics
		(dim == 2).if({
			encodingMatrix = Matrix.with(
				encodingMatrix.asArray[hoaOrder.indices(subset: \sectoral)]
			)
		});

		// 4) pseudo inverse
		decodingMatrix = encodingMatrix.pseudoInverse;

		// 4a) if 2D (re-)insert non-sectoral (3D) harmonics
		(dim == 2).if({
			zerosMatrix = Matrix.newClear(numOutputs, (outputOrder + 1).squared);
			hoaOrder.indices(subset: \sectoral).do({ |index, i|
				zerosMatrix.putCol(
					index,
					decodingMatrix.getCol(i)
				)
			});
			decodingMatrix = zerosMatrix;  // now filled
		});

		// 5) apply weights: matching weight, beam weights
		decodingMatrix = decodingMatrix.mulMatrix(weights);

		// 6) expand to match input order (if necessary)
		(inputOrder > outputOrder).if({
			decodingMatrix = (decodingMatrix.flop ++ Matrix.newClear(
				(inputOrder + 1).squared - (outputOrder + 1).squared, numOutputs)
			).flop
		});

		// assign
		matrix = decodingMatrix.zeroWithin(Hoa.nearZero)
	}

	// initDecoderVarsForFiles {
	//     if (fileParse.notNil) {
	//         dirOutputs = if (fileParse.dirOutputs.notNil) {
	//             fileParse.dirOutputs.asFloat
	//         } { // output directions are unspecified in the provided matrix
	//             matrix.rows.collect({ 'unspecified' })
	//         };
	//         shelfK = fileParse.shelfK !? {fileParse.shelfK.asFloat};
	//         shelfFreq = fileParse.shelfFreq !? {fileParse.shelfFreq.asFloat};
	//     } { // txt file provided, no fileParse
	//         dirOutputs = matrix.rows.collect({ 'unspecified' });
	//     };
	// }

	// ------------
	// Analysis

	analyzeDirections { |testDirections|
		var encDirs, xyzEncDirs, xyzDecDirs;
		var encodingMatrix;
		var g, g2, amp, energy, rms;
		var numDecHarms;
		var rVxyz, rVsphr, rVmag, rVdir, rVu;
		var rExyz, rEsphr, rEmag, rEdir, rEu;
		var spreadE;
		var rVdist, rEdist, rVrEdist;

		// reshape (test) encoding directions, as need be...
		// ... then find test unit vectors
		xyzEncDirs = testDirections.rank.switch(
			0, { Array.with(testDirections, 0.0).reshape(1, 2) },
			1, { testDirections.collect({ |dir| Array.with(dir, 0.0)}) },
			2, { testDirections },
		).collect({ |thetaPhi|
			Spherical.new(1, thetaPhi.at(0), thetaPhi.at(1)).asCartesian.asArray
		});
		// ... and back to [ theta, phi ]...
		encDirs = xyzEncDirs.collect({ |xyz|  // wrap to [ +/-pi, +/-pi/2 ]
			Cartesian.new(xyz.at(0), xyz.at(1), xyz.at(2), ).asSpherical.angles
		});

		// find decoding unit vectors
		xyzDecDirs = this.directions.collect({ |thetaPhi|
			Spherical.new(1, thetaPhi.at(0), thetaPhi.at(1)).asCartesian.asArray
		});

		// encode test directions (basic), return encoding matrix
		encodingMatrix = HoaMatrixEncoder.newDirections(
			encDirs,
			order: this.order
		).matrix;

		// decode, to return resulting loudspeaker gains (amps)
		// for each (test) encoding direction
		g = this.matrix.mulMatrix(encodingMatrix);

		// energy, expected matrix is Real only,
		// use -squared rather than -abs.squared
		g2 = g.squared;

		// pressure for each (test) encoding direction
		amp = g.sumCols;

		// energy for each (test) encoding direction
		energy = g2.sumCols;

		// rms for each (test) encoding direction
		numDecHarms = this.dim.switch(
			2, { (2 * this.order) + 1},  // 2D -- sectoral
			3, { (this.order + 1).squared }   // 3D -- all
		);
		rms = (this.numChannels/numDecHarms) * energy;

		// ------------
		// rV

		// rV vector, expected matrix is Real only
		rVxyz = g.flop.mulMatrix(Matrix.with(xyzDecDirs)) / amp;
		rVxyz = Matrix.with(rVxyz).zeroWithin(Hoa.nearZero).asArray;

		// in spherical, for convenience to find rVmag, rVdir
		rVsphr = rVxyz.collect({ |xyz|
			var x, y, z;
			#x, y, z = xyz;
			Cartesian.new(x, y, z).asSpherical
		});
		rVmag = rVsphr.collect({ |sphr| sphr.rho });
		rVdir = rVsphr.collect({ |sphr| sphr.angles });

		// normalize rVxyz to unit vector
		rVu = rVxyz / rVmag;

		// ------------
		// rE

		// rE vector, expected matrix is Real only
		rExyz = g2.flop.mulMatrix(Matrix.with(xyzDecDirs)) / energy;
		rExyz = Matrix.with(rExyz).zeroWithin(Hoa.nearZero).asArray;

		// in spherical, for convenience to find rEmag, rEdir
		rEsphr = rExyz.collect({ |xyz|
			var x, y, z;
			#x, y, z = xyz;
			Cartesian.new(x, y, z).asSpherical
		});
		rEmag = rEsphr.collect({ |sphr| sphr.rho });
		rEdir = rEsphr.collect({ |sphr| sphr.angles });

		// normalize rExyz to unit vector
		rEu = rExyz / rEmag;

		// find 1/2 angle energy spread
		spreadE = Dictionary.with(*[
			\cos->rEmag.acos,  // Zotter & Frank: ~-3dB
			\hvc->((2 * rEmag) - 1).acos  // Carpentier, Politis: ~-6dB
		]);

		// ------------
		// measure rV & rE direction distortion

		// (test) encoding directions vs rV
		rVdist = (xyzEncDirs * rVu).collect({ |item|  	// arccos(dot product)
			item.sum.acos
		});
		// (test) encoding directions vs rE
		rEdist = (xyzEncDirs * rEu).collect({ |item|  	// arccos(dot product)
			item.sum.acos
		});
		// rV vs rE
		rVrEdist = (rVu * rEu).collect({ |item|  	// arccos(dot product)
			item.sum.acos
		});

		// return
		^Dictionary.with(*[
			\amp->amp,
			\rms->rms,
			\energy->energy,
			\spreadE->spreadE,
			\rV->Dictionary.with(*[
				\xyz->rVxyz, \mag->rVmag, \directions->rVdir,
				\dist->rVdist, \rEdist->rVrEdist
			]),
			\rE->Dictionary.with(*[
				\xyz->rExyz, \mag->rEmag, \directions->rEdir,
				\dist->rEdist, \rVdist->rVrEdist
			]),
		])
	}

	analyzeAverage {
		var testMatrix;
		var amp, energy, rms;
		var meanE;
		var numCoeffs;

		// Resolve testing matrix: 2D or 3D
		this.dim.switch(
			2, {  // 2D -- N2D
				testMatrix = this.matrix.mulMatrix(
					HoaMatrixEncoder.newFormat(
						[\acn, \n2d],
						this.order
					).matrix
				);
				numCoeffs = (2 * this.order) + 1  // sectoral coeffs
			},
			3, {  // 3D -- N3D
				testMatrix = this.matrix;
				numCoeffs = (this.order + 1).squared  // all coeffs
			}
		);

		// average pressure
		amp = testMatrix.sumCol(0);

		// average energy
		energy = testMatrix.squared.sum;

		// average rms
		rms = (this.numChannels/numCoeffs) * energy;

		// meanE
		meanE = this.numChannels * energy / amp.squared;

		// return
		^Dictionary.with(*[
			\amp->amp,
			\rms->rms,
			\energy->energy,
			\meanE->meanE,
			\matchWeight->Dictionary.with(*[
				\amp->amp.reciprocal,
				\rms->rms.sqrt.reciprocal,
				\energy->energy.sqrt.reciprocal,
			])
		])
	}

}
