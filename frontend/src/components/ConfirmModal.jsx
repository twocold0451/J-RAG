import React, { useEffect, useRef, useState } from 'react';

const ConfirmModal = ({ 
  isOpen, 
  title, 
  message, 
  type = 'confirm', // 'confirm' or 'prompt'
  defaultValue = '',
  confirmText = '确定',
  cancelText = '取消',
  confirmStyle = 'btn-primary',
  onConfirm, 
  onCancel 
}) => {
  const [inputValue, setInputValue] = useState(defaultValue);
  const inputRef = useRef(null);

  useEffect(() => {
    if (isOpen) {
      setInputValue(defaultValue);
      // Focus input on prompt open
      if (type === 'prompt' && inputRef.current) {
        setTimeout(() => inputRef.current.focus(), 100);
      }
    }
  }, [isOpen, defaultValue, type]);

  if (!isOpen) return null;

  const handleConfirm = () => {
    if (type === 'prompt') {
      onConfirm(inputValue);
    } else {
      onConfirm();
    }
  };

  return (
    <div className="modal modal-open z-[100]">
      <div className="modal-box">
        <h3 className="font-bold text-lg">{title}</h3>
        <p className="py-4 whitespace-pre-wrap">{message}</p>
        
        {type === 'prompt' && (
          <div className="form-control w-full">
            <input 
              ref={inputRef}
              type="text" 
              className="input input-bordered w-full" 
              value={inputValue} 
              onChange={(e) => setInputValue(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleConfirm();
                if (e.key === 'Escape') onCancel();
              }}
            />
          </div>
        )}

        <div className="modal-action">
          <button className="btn" onClick={onCancel}>{cancelText}</button>
          <button className={`btn ${confirmStyle}`} onClick={handleConfirm}>{confirmText}</button>
        </div>
      </div>
      <div className="modal-backdrop bg-black/50" onClick={onCancel}></div>
    </div>
  );
};

export default ConfirmModal;
