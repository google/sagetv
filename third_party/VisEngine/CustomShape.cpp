/**
 * projectM -- Milkdrop-esque visualisation SDK
 * Copyright (C)2003-2004 projectM Team
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed i the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * See 'LICENSE.txt' included within this release
 *
 */

#include <stdio.h>
#include <string.h>
#include <stdlib.h>

#include "Common.hpp"
#include "fatal.h"

#include "CustomShape.hpp"
#include "Eval.hpp"
#include "Expr.hpp"
#include "InitCond.hpp"
#include "Param.hpp"
#include "PerFrameEqn.hpp"
#include "Preset.hpp"
#include <map>
#include "ParamUtils.hpp"
#include "InitCondUtils.hpp"
#include "wipemalloc.h"
#include "Algorithms.hpp"


using namespace Algorithms;


CustomShape::CustomShape ( int id ) : imageUrl("")
{

	Param * param;

	this->id = id;
	this->per_frame_count = 0;

	/* Start: Load custom shape parameters */
	param = Param::new_param_mathtype ( "r", P_FLAG_NONE, &this->r, NULL, mathval(1.0f), mathval(0.0f), mathval(0.5f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_mathtype ( "g", P_FLAG_NONE, &this->g, NULL, mathval(1.0f), mathval(0.0f), mathval(.5f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_mathtype ( "b", P_FLAG_NONE, &this->b, NULL, mathval(1.0f), mathval(0.0f), mathval(.5f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_mathtype ( "a", P_FLAG_NONE, &this->a, NULL, mathtype(1.0f), mathval(0.0f), mathtype(.5f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_mathtype ( "border_r", P_FLAG_NONE, &this->border_r, NULL, mathtype(1.0f), mathval(0.0f), mathtype(.5f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_mathtype ( "border_g", P_FLAG_NONE, &this->border_g, NULL, mathtype(1.0f), mathval(0.0f), mathtype(.5f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_mathtype ( "border_b", P_FLAG_NONE, &this->border_b, NULL, mathtype(1.0f), mathval(0.0f), mathtype(.5f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_mathtype ( "border_a", P_FLAG_NONE, &this->border_a, NULL, mathtype(1.0f), mathval(0.0f), mathtype(.5f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_mathtype ( "r2", P_FLAG_NONE, &this->r2, NULL, mathtype(1.0f), mathval(0.0f), mathtype(.5f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_mathtype ( "g2", P_FLAG_NONE, &this->g2, NULL, mathtype(1.0f), mathval(0.0f), mathtype(.5f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_mathtype ( "b2", P_FLAG_NONE, &this->b2, NULL, mathtype(1.0f), mathval(0.0f), mathtype(.5f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_mathtype ( "a2", P_FLAG_NONE, &this->a2, NULL, mathtype(1.0f), mathval(0.0f), mathtype(.5f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_mathtype ( "x", P_FLAG_NONE, &this->x, NULL, mathtype(1.0f), mathval(0.0f), mathtype(.5f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_mathtype ( "y", P_FLAG_NONE, &this->y, NULL, mathtype(1.0f), mathval(0.0f), mathtype(.5f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_bool ( "thickoutline", P_FLAG_NONE, &this->thickOutline, 1, 0, 0 );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_bool ( "enabled", P_FLAG_NONE, &this->enabled, 1, 0, 0 );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_int ( "sides", P_FLAG_NONE, &this->sides, 100, 3, 3 );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_bool ( "additive", P_FLAG_NONE, &this->additive, 1, 0, 0 );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_bool ( "textured", P_FLAG_NONE, &this->textured, 1, 0, 0 );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_mathtype ( "rad", P_FLAG_NONE, &this->radius, NULL, MAX_FLOAT_SIZE, 0, mathval(0.0f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort(); 
	}
	param = Param::new_param_mathtype ( "ang", P_FLAG_NONE, &this->ang, NULL, MAX_FLOAT_SIZE, -MAX_FLOAT_SIZE, mathval(0.0f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_mathtype ( "tex_zoom", P_FLAG_NONE, &this->tex_zoom, NULL, MAX_FLOAT_SIZE, mathval(.00002f), mathval(0.0f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_mathtype ( "tex_ang", P_FLAG_NONE, &this->tex_ang, NULL, MAX_FLOAT_SIZE, -MAX_FLOAT_SIZE, mathval(0.0f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}

	param = Param::new_param_mathtype ( "t1", P_FLAG_TVAR, &this->t1, NULL, MAX_FLOAT_SIZE, -MAX_FLOAT_SIZE, mathval(0.0f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_mathtype ( "t2", P_FLAG_TVAR, &this->t2, NULL, MAX_FLOAT_SIZE, -MAX_FLOAT_SIZE, mathval(0.0f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_mathtype ( "t3", P_FLAG_TVAR, &this->t3, NULL, MAX_FLOAT_SIZE, -MAX_FLOAT_SIZE, mathval(0.0f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_mathtype ( "t4", P_FLAG_TVAR, &this->t4, NULL, MAX_FLOAT_SIZE, -MAX_FLOAT_SIZE, mathval(0.0f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_mathtype ( "t5", P_FLAG_TVAR, &this->t5, NULL, MAX_FLOAT_SIZE, -MAX_FLOAT_SIZE, mathval(0.0f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_mathtype ( "t6", P_FLAG_TVAR, &this->t6, NULL, MAX_FLOAT_SIZE, -MAX_FLOAT_SIZE, mathval(0.0f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();		
	}
	param = Param::new_param_mathtype ( "t7", P_FLAG_TVAR, &this->t7, NULL, MAX_FLOAT_SIZE, -MAX_FLOAT_SIZE, mathval(0.0f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_mathtype ( "t8", P_FLAG_TVAR, &this->t8, NULL, MAX_FLOAT_SIZE, -MAX_FLOAT_SIZE, mathval(0.0f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}

	param = Param::new_param_mathtype ( "q1", P_FLAG_QVAR, &this->q1, NULL, MAX_FLOAT_SIZE, -MAX_FLOAT_SIZE, mathval(0.0f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_mathtype ( "q2", P_FLAG_QVAR, &this->q2, NULL, MAX_FLOAT_SIZE, -MAX_FLOAT_SIZE, mathval(0.0f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_mathtype ( "q3", P_FLAG_QVAR, &this->q3, NULL, MAX_FLOAT_SIZE, -MAX_FLOAT_SIZE, mathval(0.0f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_mathtype ( "q4", P_FLAG_QVAR, &this->q4, NULL, MAX_FLOAT_SIZE, -MAX_FLOAT_SIZE, mathval(0.0f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_mathtype ( "q5", P_FLAG_QVAR, &this->q5, NULL, MAX_FLOAT_SIZE, -MAX_FLOAT_SIZE, mathval(0.0f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_mathtype ( "q6", P_FLAG_QVAR, &this->q6, NULL, MAX_FLOAT_SIZE, -MAX_FLOAT_SIZE, mathval(0.0f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_mathtype ( "q7", P_FLAG_QVAR, &this->q7, NULL, MAX_FLOAT_SIZE, -MAX_FLOAT_SIZE, mathval(0.0f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}
	param = Param::new_param_mathtype ( "q8", P_FLAG_QVAR, &this->q8, NULL, MAX_FLOAT_SIZE, -MAX_FLOAT_SIZE, mathval(0.0f) );
	if ( !ParamUtils::insert( param, &this->param_tree ) )
	{
		abort();
	}

	param = Param::new_param_string ( "imageurl", P_FLAG_NONE, &this->imageUrl);
	if ( !ParamUtils::insert( param, &this->text_properties_tree ) )
	{
		abort();
	}

}

/* Frees a custom shape form object */
CustomShape::~CustomShape()
{

	traverseVector<TraverseFunctors::DeleteFunctor<PerFrameEqn> > ( per_frame_eqn_tree );
	traverse<TraverseFunctors::DeleteFunctor<InitCond> > ( init_cond_tree );
	traverse<TraverseFunctors::DeleteFunctor<Param> > ( param_tree );
	traverse<TraverseFunctors::DeleteFunctor<InitCond> > ( per_frame_init_eqn_tree );
	traverse<TraverseFunctors::DeleteFunctor<Param> > ( text_properties_tree );

}

void CustomShape::loadUnspecInitConds()
{

	// NOTE: This is verified to be same behavior as trunk

	InitCondUtils::LoadUnspecInitCond fun ( this->init_cond_tree, this->per_frame_init_eqn_tree );
	traverse ( param_tree, fun );
}

void CustomShape::evalInitConds()
{

	// NOTE: This is verified to be same behavior as trunk
	for ( std::map<std::string, InitCond*>::iterator pos = per_frame_init_eqn_tree.begin(); pos != per_frame_init_eqn_tree.end();++pos )
		pos->second->evaluate();
}
