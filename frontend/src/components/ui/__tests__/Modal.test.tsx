import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import '@testing-library/jest-dom';
import Modal from '../Modal';

describe('Modal Component', () => {
  it('should render title and children when open', () => {
    const handleClose = vi.fn();
    render(
      <Modal open={true} onClose={handleClose} title="Test Modal">
        <div>Content inside modal</div>
      </Modal>
    );

    expect(screen.getByText('Test Modal')).toBeInTheDocument();
    expect(screen.getByText('Content inside modal')).toBeInTheDocument();
  });

  it('should not render when open is false', () => {
    const handleClose = vi.fn();
    render(
      <Modal open={false} onClose={handleClose} title="Test Modal">
        <div>Content inside modal</div>
      </Modal>
    );

    expect(screen.queryByText('Test Modal')).not.toBeInTheDocument();
    expect(screen.queryByText('Content inside modal')).not.toBeInTheDocument();
  });

  it('should call onClose when close button is clicked', () => {
    const handleClose = vi.fn();
    render(
      <Modal open={true} onClose={handleClose} title="Test Modal">
        <div>Content</div>
      </Modal>
    );

    const closeButton = screen.getByRole('button');
    fireEvent.click(closeButton);

    expect(handleClose).toHaveBeenCalledTimes(1);
  });
});
