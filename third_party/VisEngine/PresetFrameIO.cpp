#include "PresetFrameIO.hpp"
#include "wipemalloc.h"
#include <math.h>
#include <cassert>
#include <iostream>
PresetInputs::PresetInputs()
{
}

void PresetInputs::Initialize ( int gx, int gy )
{
	int x, y;

	this->gx =gx;
	this->gy= gy;

	this->x_mesh= ( mathtype ** ) wipemalloc ( gx * sizeof ( mathtype * ) );
	for ( x = 0; x < gx; x++ )
	{
		this->x_mesh[x] = ( mathtype * ) wipemalloc ( gy * sizeof ( mathtype ) );
	}
	this->y_mesh= ( mathtype ** ) wipemalloc ( gx * sizeof ( mathtype * ) );
	for ( x = 0; x < gx; x++ )
	{
		this->y_mesh[x] = ( mathtype * ) wipemalloc ( gy * sizeof ( mathtype ) );
	}
	this->rad_mesh= ( mathtype ** ) wipemalloc ( gx * sizeof ( mathtype * ) );
	for ( x = 0; x < gx; x++ )
	{
		this->rad_mesh[x] = ( mathtype * ) wipemalloc ( gy * sizeof ( mathtype ) );
	}
	this->theta_mesh= ( mathtype ** ) wipemalloc ( gx * sizeof ( mathtype * ) );
	for ( x = 0; x <gx; x++ )
	{
		this->theta_mesh[x] = ( mathtype * ) wipemalloc ( gy * sizeof ( mathtype ) );
	}

	this->origtheta= ( mathtype ** ) wipemalloc ( gx * sizeof ( mathtype * ) );
	for ( x = 0; x < gx; x++ )
	{
		this->origtheta[x] = ( mathtype * ) wipemalloc ( gy * sizeof ( mathtype ) );
	}
	this->origrad= ( mathtype ** ) wipemalloc ( gx * sizeof ( mathtype * ) );
	for ( x = 0; x < gx; x++ )
	{
		this->origrad[x] = ( mathtype * ) wipemalloc ( gy * sizeof ( mathtype ) );
	}
	this->origx= ( mathtype ** ) wipemalloc ( gx * sizeof ( mathtype * ) );
	for ( x = 0; x < gx; x++ )
	{
		this->origx[x] = ( mathtype * ) wipemalloc ( gy * sizeof ( mathtype ) );
	}
	this->origy= ( mathtype ** ) wipemalloc ( gx * sizeof ( mathtype * ) );
	for ( x = 0; x < gx; x++ )
	{
		this->origy[x] = ( mathtype * ) wipemalloc ( gy * sizeof ( mathtype ) );
	}

	for ( x=0;x<gx;x++ )
	{
		for ( y=0;y<gy;y++ )
		{
			this->origx[x][y]=mathval(x/ ( float ) ( gx-1 ));
			this->origy[x][y]=mathval(- ( ( y/ ( float ) ( gy-1 ) )-1 ));
			this->origrad[x][y]=mathval( hypotf ( ( mathtofloat(this->origx[x][y])-.5f ) *2, ( mathtofloat(this->origy[x][y])-.5f ) *2 ) * .7071067f);
			this->origtheta[x][y]=mathval(atan2 ( ( ( mathtofloat(this->origy[x][y])-.5f ) *2 ), ( ( mathtofloat(this->origx[x][y])-.5f ) *2 ) ));
		}
	}



}

PresetOutputs::PresetOutputs()
{}

PresetOutputs::~PresetOutputs()
{
	assert(this->gx > 0);

	for ( int x = 0; x < this->gx; x++ )
	{


		free(this->x_mesh[x]);
		free(this->y_mesh[x]);
		free(this->sx_mesh[x]);
		free(this->sy_mesh[x]);
		free(this->dy_mesh[x]);
		free(this->dx_mesh[x]);
		free(this->cy_mesh[x]);
		free(this->cx_mesh[x]);
		
		free(this->warp_mesh[x]);
		free(this->zoom_mesh[x]);
		free(this->zoomexp_mesh[x]);
		free(this->rot_mesh[x]);
		
	}

		free(this->x_mesh);
		free(this->y_mesh);
		free(this->sx_mesh);
		free(this->sy_mesh);
		free(this->dy_mesh);
		free(this->dx_mesh);
		free(this->cy_mesh);
		free(this->cx_mesh);
		free(this->warp_mesh);
		free(this->zoom_mesh);
		free(this->zoomexp_mesh);
		free(this->rot_mesh);
}

void PresetOutputs::Initialize ( int gx, int gy )
{

	assert(gx > 0);
	this->gx = gx;
	this->gy= gy;
	assert(this->gx > 0);
	int x;
	this->x_mesh= ( mathtype ** ) wipemalloc ( gx * sizeof ( mathtype * ) );
	for ( x = 0; x < gx; x++ )
	{
		this->x_mesh[x] = ( mathtype * ) wipemalloc ( gy * sizeof ( mathtype ) );
	}
	this->y_mesh= ( mathtype ** ) wipemalloc ( gx * sizeof ( mathtype * ) );
	for ( x = 0; x < gx; x++ )
	{
		this->y_mesh[x] = ( mathtype * ) wipemalloc ( gy * sizeof ( mathtype ) );
	}
	this->sx_mesh= ( mathtype ** ) wipemalloc ( gx * sizeof ( mathtype * ) );
	for ( x = 0; x < gx; x++ )
	{
		this->sx_mesh[x] = ( mathtype * ) wipemalloc ( gy * sizeof ( mathtype ) );
	}
	this->sy_mesh= ( mathtype ** ) wipemalloc ( gx * sizeof ( mathtype * ) );
	for ( x = 0; x < gx; x++ )
	{
		this->sy_mesh[x] = ( mathtype * ) wipemalloc ( gy * sizeof ( mathtype ) );
	}
	this->dx_mesh= ( mathtype ** ) wipemalloc ( gx * sizeof ( mathtype * ) );
	for ( x = 0; x < gx; x++ )
	{
		this->dx_mesh[x] = ( mathtype * ) wipemalloc ( gy * sizeof ( mathtype ) );
	}
	this->dy_mesh= ( mathtype ** ) wipemalloc ( gx * sizeof ( mathtype * ) );
	for ( x = 0; x < gx; x++ )
	{
		this->dy_mesh[x] = ( mathtype * ) wipemalloc ( gy * sizeof ( mathtype ) );
	}
	this->cx_mesh= ( mathtype ** ) wipemalloc ( gx * sizeof ( mathtype * ) );
	for ( x = 0; x < gx; x++ )
	{
		this->cx_mesh[x] = ( mathtype * ) wipemalloc ( gy * sizeof ( mathtype ) );
	}
	this->cy_mesh= ( mathtype ** ) wipemalloc ( gx * sizeof ( mathtype * ) );
	for ( x = 0; x < gx; x++ )
	{
		this->cy_mesh[x] = ( mathtype * ) wipemalloc ( gy * sizeof ( mathtype ) );
	}
	this->zoom_mesh= ( mathtype ** ) wipemalloc ( gx * sizeof ( mathtype * ) );
	for ( x = 0; x < gx; x++ )
	{
		this->zoom_mesh[x] = ( mathtype * ) wipemalloc ( gy * sizeof ( mathtype ) );
	}
	this->zoomexp_mesh= ( mathtype ** ) wipemalloc ( gx * sizeof ( mathtype * ) );
	for ( x = 0; x < gx; x++ )
	{
		this->zoomexp_mesh[x] = ( mathtype * ) wipemalloc ( gy * sizeof ( mathtype ) );
	}
	this->rot_mesh= ( mathtype ** ) wipemalloc ( gx * sizeof ( mathtype * ) );
	for ( x = 0; x < gx; x++ )
	{
		this->rot_mesh[x] = ( mathtype * ) wipemalloc ( gy * sizeof ( mathtype ) );
	}

	this->warp_mesh= ( mathtype ** ) wipemalloc ( gx * sizeof ( mathtype * ) );
	for ( x = 0; x < gx; x++ )
	{
		this->warp_mesh[x] = ( mathtype * ) wipemalloc ( gy * sizeof ( mathtype ) );
	}


}

PresetInputs::~PresetInputs()
{
	for ( int x = 0; x < this->gx; x++ )
	{


		free ( this->origtheta[x] );
		free ( this->origrad[x] );
		free ( this->origx[x] );
		free ( this->origy[x] );

		free ( this->x_mesh[x] );
		free ( this->y_mesh[x] );
		free ( this->rad_mesh[x] );
		free ( this->theta_mesh[x] );

	}


	free ( this->origx );
	free ( this->origy );
	free ( this->origrad );
	free ( this->origtheta );

	free ( this->x_mesh );
	free ( this->y_mesh );
	free ( this->rad_mesh );
	free ( this->theta_mesh );

	this->origx = NULL;
	this->origy = NULL;
	this->origtheta = NULL;
	this->origrad = NULL;

	this->x_mesh = NULL;
	this->y_mesh = NULL;
	this->rad_mesh = NULL;
	this->theta_mesh = NULL;
}


void PresetInputs::ResetMesh()
{
	int x,y;

	assert ( x_mesh );
	assert ( y_mesh );
	assert ( rad_mesh );
	assert ( theta_mesh );

	for ( x=0;x<this->gx;x++ )
	{
		for ( y=0;y<this->gy;y++ )
		{
			x_mesh[x][y]=this->origx[x][y];
			y_mesh[x][y]=this->origy[x][y];
			rad_mesh[x][y]=this->origrad[x][y];
			theta_mesh[x][y]=this->origtheta[x][y];
		}
	}

}
