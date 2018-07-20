/*
	Copyright the ATK Community, Joseph Anderson, and Michael McCrea, 2018
		J Anderson	j.anderson[at]ambisonictoolkit.net


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
// 	Class: HoaRadius
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


//------------------------------------------------------------------------
// Radius Utilities

HoaRadius {
    var <radius;

    *new { |radius = 0.37220553736718|
        ^super.newCopyArgs(radius)
    }

    // Set radius from delay (in seconds).
    *newDelay { |delay = 0.0010851473392629|
        ^this.new(delay*Hoa.speedOfSound)
    }

    // Set radius from wavenumber and order
    *newWaveNumber { |waveNumber = 8.0600627847202, order|
		var n = order ?? { Hoa.defaultOrder };
        ^this.new(n/waveNumber)
    }

    // Set radius from frequency and order
    *newFreq { |freq = 440.0, order|
		var n = order ?? { Hoa.defaultOrder };
        ^this.new((n*Hoa.speedOfSound) / (2*pi*freq))
    }

    // Set radius from normalised frequency and order
    *newWn { |wn, sampleRate, order|
		var n = order ?? { Hoa.defaultOrder };
        ^this.new((n*Hoa.speedOfSound) / (pi*wn*sampleRate))
    }


    // Return reference delay.
    delay {
        ^this.radius / Hoa.speedOfSound
    }

    // ----------
    // Order

    // Return effective order.
    orderAtWaveNumber { |waveNumber = 8.0600627847202|
        ^(this.radius*waveNumber)
    }

    // Return effective order.
    orderAtFreq { |freq = 440.0|
        ^(2*pi*this.radius*freq) / Hoa.speedOfSound
    }

    // Return effective order.
    orderAtWn { |wn, sampleRate|
        ^(pi*this.radius*wn*sampleRate) / Hoa.speedOfSound
    }

    // ----------
    // Wavenumber / frequency

    // Return effective wavenumber.
    waveNumber { |order|
		var n = order ?? { Hoa.defaultOrder };
        ^(n/this.radius)
    }

    // Return effective frequency.
    freq { |order|
		var n = order ?? { Hoa.defaultOrder };
        ^(n*Hoa.speedOfSound) / (2*pi*this.radius)
    }

    // Return effective normalized frequency.
    wn { |sampleRate, order|
		var n = order ?? { Hoa.defaultOrder };
        ^(n*Hoa.speedOfSound) / (pi*this.radius*sampleRate)
    }

}
