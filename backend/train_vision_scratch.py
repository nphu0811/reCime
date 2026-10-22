"""Train a small dish-only image classifier from scratch using HOG + linear SVM.

No pretrained weights. Folder labels must map to catalog keys in labels.json.
Requires >=5 distinct images/class. Splits real source images BEFORE extraction;
near-duplicate cleanup/curation is still the dataset owner's responsibility.
Never trains on a single image copied/augmented into train and validation.
"""
from pathlib import Path
import argparse,hashlib,json,time
import numpy as np
from PIL import Image,ImageOps
from sklearn.model_selection import train_test_split
from sklearn.linear_model import SGDClassifier
from sklearn.metrics import accuracy_score,balanced_accuracy_score,confusion_matrix

def features(path):
    with Image.open(path) as image:
        image=ImageOps.fit(ImageOps.exif_transpose(image).convert('RGB'),(64,64))
        array=np.asarray(image,dtype=np.float32)/255
    # A compact, deterministic gradient+colour descriptor. Classifier learns all weights.
    gray=array.mean(axis=2);gy,gx=np.gradient(gray);angle=(np.arctan2(gy,gx)+np.pi)%(2*np.pi);magnitude=np.sqrt(gx*gx+gy*gy)
    bins=np.minimum((angle/(2*np.pi)*9).astype(int),8);values=[]
    for y in range(0,64,8):
        for x in range(0,64,8):
            histogram=np.bincount(bins[y:y+8,x:x+8].ravel(),weights=magnitude[y:y+8,x:x+8].ravel(),minlength=9)
            values.extend(histogram/(np.linalg.norm(histogram)+1e-6))
            values.extend(array[y:y+8,x:x+8].mean(axis=(0,1)))
    return np.asarray(values,dtype=np.float32)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('data',type=Path);parser.add_argument('--output',type=Path,default=Path('backend/models/vision-scratch'));args=parser.parse_args()
    mapping=json.loads((args.data/'labels.json').read_text(encoding='utf-8'))
    paths=[];labels=[];hashes=set();counts={}
    for label in sorted(mapping):
        items=[]
        for file in sorted((args.data/label).glob('*')):
            if file.suffix.lower() not in ['.jpg','.jpeg','.png','.webp']:continue
            digest=hashlib.sha256(file.read_bytes()).hexdigest()
            if digest in hashes:continue
            hashes.add(digest);items.append(file)
        if len(items)<5:raise SystemExit(f'{label}: cần ít nhất5 ảnh gốc khác nhau, hiện có{len(items)}. Không tự tạo accuracy từ ảnh lặp.')
        counts[label]=len(items);paths.extend(items);labels.extend([label]*len(items))
    if len(counts)<2:raise SystemExit('Cần ít nhất2 loại món có ảnh được phép dùng.')
    train,test=train_test_split(np.arange(len(paths)),test_size=.25,random_state=42,stratify=labels)
    x=np.stack([features(p) for p in paths]);y=np.asarray(labels)
    model=SGDClassifier(loss='modified_huber',alpha=.001,max_iter=2000,tol=1e-5,random_state=42,class_weight='balanced')
    model.fit(x[train],y[train]);pred=model.predict(x[test])
    args.output.mkdir(parents=True,exist_ok=True)
    np.savez_compressed(args.output/'weights.npz',coef=model.coef_,intercept=model.intercept_,classes=model.classes_)
    majority_label=max(set(y[train]),key=lambda label:int(np.sum(y[train]==label)))
    majority_accuracy=float(np.mean(y[test]==majority_label))
    report={'architecture':'64x64 image gradient+colour descriptor -> linear SGD classifier; all classifier weights trained from random initialization',
        'classCounts':counts,'trainCount':len(train),'validationCount':len(test),'validationAccuracy':accuracy_score(y[test],pred),
        'balancedAccuracy':balanced_accuracy_score(y[test],pred),'confusionMatrix':confusion_matrix(y[test],pred,labels=model.classes_).tolist(),
        'labels':mapping,'splitSeed':42,'majorityBaselineAccuracy':majority_accuracy,
        'approvedForRecipeSelection':False,
        'warning':'Experimental coarse-category model only. Compare with majority baseline; not approved for recipe selection. External evaluation and food/non-food rejection required before production.'}
    (args.output/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False,indent=2))

if __name__=='__main__':main()
