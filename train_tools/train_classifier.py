# -*- coding: utf-8 -*-
"""
训练 80x80 棋子分类模型（16类），导出 ONNX 供 Android ONNX Runtime Mobile 使用。
依赖：pip install torch torchvision onnx
素材：把每种棋子的透明PNG按类别名放入 pieces/ 目录，如 pieces/R.png pieces/r.png …
类别顺序必须与 App Constants.CLASS_MAP 一致。
"""
import os
import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader
from torchvision import transforms
from PIL import Image

CLASSES = ['rP','rR','rK','rN','rC','rA','rB','-','.',
           'R','P','K','N','C','A','B']
# 与 App jj_piece_map.json 对齐: 0:a 1:b 2:c 3:k 4:n 5:p 6:r 7:- 8:. 9:A 10:B 11:C 12:K 13:N 14:P 15:R
# 若你的素材命名不同，调整下方 NAME2IDX 映射。
NAME2IDX = {
    'a':1,'b':2,'c':3,'k':4,'n':5,'p':6,'-':7,'.':8,
    'A':9,'B':10,'C':11,'K':12,'N':13,'P':14,'R':15,
    'r':6,'rP':0,  # 兼容命名
}

class PieceDS(Dataset):
    def __init__(self, root, tf):
        self.items = []
        for fn in os.listdir(root):
            if not fn.lower().endswith(('.png','.jpg')):
                continue
            key = os.path.splitext(fn)[0]
            if key in NAME2IDX:
                self.items.append((os.path.join(root, fn), NAME2IDX[key]))
        self.tf = tf
    def __len__(self): return len(self.items)
    def __getitem__(self, i):
        p, y = self.items[i]
        img = Image.open(p).convert('RGB')
        return self.tf(img), y

class SmallCNN(nn.Module):
    def __init__(self, n=16):
        super().__init__()
        self.f = nn.Sequential(
            nn.Conv2d(3,32,3,padding=1), nn.ReLU(), nn.MaxPool2d(2),
            nn.Conv2d(32,64,3,padding=1), nn.ReLU(), nn.MaxPool2d(2),
            nn.Conv2d(64,128,3,padding=1), nn.ReLU(), nn.MaxPool2d(2),
            nn.Flatten(), nn.Linear(128*10*10, 256), nn.ReLU(), nn.Linear(256, n))
    def forward(self,x): return self.f(x)

def main():
    tf = transforms.Compose([
        transforms.Resize((80,80)), transforms.ToTensor()])
    ds = PieceDS('pieces', tf)
    if len(ds)==0:
        print('未在 pieces/ 找到素材，请先放入棋子PNG'); return
    dl = DataLoader(ds, batch_size=32, shuffle=True)
    net = SmallCNN()
    opt = torch.optim.Adam(net.parameters(), lr=1e-3)
    lossf = nn.CrossEntropyLoss()
    for ep in range(30):
        tot=0
        for x,y in dl:
            opt.zero_grad(); out=net(x); loss=lossf(out,y); loss.backward(); opt.step()
            tot+=loss.item()
        print(f'epoch {ep+1} loss {tot/len(dl):.4f}')
    torch.onnx.export(net, torch.randn(1,3,80,80), 'jj_piece.onnx',
                      input_names=['input'], output_names=['output'], opset_version=11)
    print('导出 jj_piece.onnx 完成')

if __name__=='__main__': main()
