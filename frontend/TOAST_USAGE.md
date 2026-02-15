# Toast Notification System

## Overview
Replace all `console.log()` statements with toast notifications that appear on the right side of the screen and fade out automatically.

## Usage

### Import the hook
```javascript
import { useToast } from '../contexts/ToastContext';
```

### In your component
```javascript
function MyComponent() {
  const toast = useToast();

  const handleAction = () => {
    // Success notification (green)
    toast.success('Operation completed successfully!');
    
    // Error notification (red)
    toast.error('Something went wrong!');
    
    // Warning notification (orange)
    toast.warning('Please check your input');
    
    // Info notification (blue)
    toast.info('Loading data...');
    
    // Custom duration (default is 3000ms)
    toast.success('This will stay for 5 seconds', 5000);
  };
}
```

## Migration Guide

### Before (console.log)
```javascript
console.log('Login successful');
console.error('Login failed:', error);
console.warn('No user found');
```

### After (toast notifications)
```javascript
const toast = useToast();

toast.success('Login successful');
toast.error('Login failed: ' + error.message);
toast.warning('No user found');
```

## Types Available
- `toast.success()` - Green notification with checkmark
- `toast.error()` - Red notification with X
- `toast.warning()` - Orange notification with warning symbol
- `toast.info()` - Blue notification with info symbol

## Features
- ✓ Appears on the right side of screen
- ✓ Automatically fades out after 3 seconds (customizable)
- ✓ Slide-in animation
- ✓ Can be manually closed with X button
- ✓ Multiple toasts stack vertically
- ✓ Color-coded by type
