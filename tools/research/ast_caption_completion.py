"""Report-content checks; a matching artifact digest alone cannot approve a run."""
import math
import re

from infant_presence_probe import supported


def scalar(value, low=0., high=1.):
    if type(value) not in (int,float) or not math.isfinite(value) or not low <= value <= high:
        raise ValueError('Missing or invalid report scalar')
    return value


def flags(report, values):
    for name, expected in values.items():
        if type(report.get(name)) is not type(expected) or report[name] != expected:
            raise ValueError('Report scope differs: '+name)


def case_ids(report, expected):
    rows=report.get('cases')
    if not isinstance(rows,list) or [row.get('id') for row in rows] != list(expected):
        raise ValueError('Missing, partial or reordered report cases')
    return rows


def training_selection(report, training):
    flags(report,dict(validation_features_read=False,private_features_read=False))
    ids=[row['id'] for row in training];groups=sorted({row['sourcegroup'] for row in training})
    if len(ids)!=23 or len(set(ids))!=23 or len(groups)!=20 or report.get('train_ids')!=ids:
        raise ValueError('Training selection IDs/partition missing')
    grids=report.get('source_cv')
    if not isinstance(grids,list) or [g.get('penalty') for g in grids]!=[.0001,.001,.01]:
        raise ValueError('Prescribed selection grid missing')
    for grid in grids:
        folds=grid.get('folds')
        if not isinstance(folds,list) or [fold.get('sourcegroup') for fold in folds]!=groups:
            raise ValueError('Incomplete source-CV folds')
        for fold,group in zip(folds,groups):
            held=[row for row in training if row['sourcegroup']==group]
            if (fold.get('held_ids')!=[row['id'] for row in held]
                    or fold.get('train_ids')!=[row['id'] for row in training if row['sourcegroup']!=group]
                    or fold.get('held_weak_classes')!=sorted({row['weak_class'] for row in held})):
                raise ValueError('CV train/held source separation differs')
            scalar(fold.get('negative_log_likelihood'),0.,float('inf'))
        losses=[fold['negative_log_likelihood'] for fold in folds]
        if abs(scalar(grid.get('source_macro_nll'),0.,float('inf'))-sum(losses)/20)>1e-12:
            raise ValueError('Source macro loss contradicts folds')
        for label in ('infant_vocal','adult_vocal','other_or_uncertain'):
            values=[fold['negative_log_likelihood'] for fold in folds if label in fold['held_weak_classes']]
            macro=grid.get('class_macro_nll',{}).get(label)
            if not values or abs(scalar(macro,0.,float('inf'))-sum(values)/len(values))>1e-12:
                raise ValueError('Class macro loss missing or contradictory')
    chosen=min(grids,key=lambda row:(row['source_macro_nll'],-row['penalty']))['penalty']
    if report.get('selected_penalty')!=chosen:raise ValueError('Selection does not match prescribed CV minimum')


def score_frame(row, score_name='infant_score', support_name='infant_support'):
    quiet=scalar(row.get('rms')) < .003
    if row.get('quiet') is not quiet:
        raise ValueError('Quiet report contradicts same-window RMS')
    if quiet:
        if row.get(score_name,'missing') is not None or row.get(support_name) is not False:
            raise ValueError('Quiet window cannot approve support')
    else:
        value=scalar(row.get(score_name))
        if type(row.get(support_name)) is not bool or row[support_name] != supported(value):
            raise ValueError('Support report contradicts frozen threshold')


def frames(rows, samples, *, scored=True, dual=False):
    if type(samples) is not int or not 1 <= samples <= (320000 if dual else 1920000) or not isinstance(rows,list):
        raise ValueError('Report sample/frames missing')
    if len(rows)!=(samples+159999)//160000:
        raise ValueError('Incomplete whole-recording coverage')
    for index,row in enumerate(rows):
        real=min(160000,samples-index*160000)
        flags(row,dict(start_sample=index*160000,real_samples=real,padded_samples=max(0,16000-real)))
        scalar(row.get('rms'))
        if not isinstance(row.get('input_pcm_sha256'),str) or not re.fullmatch('[a-f0-9]{64}',row['input_pcm_sha256']):
            raise ValueError('Missing PCM window pin')
        if dual:
            for name in ('base','caption'):
                score_frame(row,name+'_score',name+'_support')
                if not row['quiet']:scalar(row.get(name+'_float32_error'),0.,5e-5)
            if row.get('paired_support') is not (row['base_support'] or row['caption_support']):
                raise ValueError('Pair summary contradicts separate supports')
            if not row['quiet']:scalar(row.get('original_graph_base_error'),0.,5e-5)
        else:
            if row.get('exact_527_logits_equal') is not True or scalar(row.get('max_absolute_logit_error')) != 0:
                raise ValueError('Original 527 output parity missing')
            if scored:
                score_frame(row)
                if not row['quiet']:scalar(row.get('float32_absolute_error'),0.,5e-5)
